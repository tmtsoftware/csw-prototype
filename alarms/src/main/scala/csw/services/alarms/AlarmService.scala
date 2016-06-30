package csw.services.alarms

import java.io._
import java.net.URI

import akka.actor.{ActorRef, ActorRefFactory, PoisonPill}
import akka.util.Timeout
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.load.download.URIDownloader
import com.github.fge.jsonschema.core.report.{ProcessingMessage, ProcessingReport}
import com.github.fge.jsonschema.main.{JsonSchema, JsonSchemaFactory}
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.alarms.AlarmModel.{AlarmStatus, SeverityLevel}
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import csw.services.loc.Connection.HttpConnection
import csw.services.loc.LocationService.ResolvedHttpLocation
import org.slf4j.LoggerFactory
import redis._

import scala.concurrent.{Await, Future}

/**
 * Static definitions for the Alarm Service
 */
object AlarmService {
  private val jsonOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
  private val cfg = LoadingConfiguration.newBuilder.addScheme("config", ConfigDownloader).freeze
  private val factory = JsonSchemaFactory.newBuilder.setLoadingConfiguration(cfg).freeze
  private[alarms] val logger = Logger(LoggerFactory.getLogger("AlarmService"))

  /**
   * The default name that the Alarm Service is registered with
   */
  val defaultName = "Alarm Service"

  /**
   * The default number of seconds before an alarm severity level expires and becomes Indeterminate
   */
  val defaultExpireSecs = 15

  // Lookup the alarm service redis instance with the location service
  private def locateAlarmService(asName: String = "")(implicit system: ActorRefFactory, timeout: Timeout): Future[RedisClient] = {
    import system.dispatcher
    val connection = HttpConnection(ComponentId(asName, ComponentType.Service))
    LocationService.resolve(Set(connection)).map {
      case locationsReady ⇒
        val uri = locationsReady.locations.head.asInstanceOf[ResolvedHttpLocation].uri
        RedisClient(uri.getHost, uri.getPort)
    }
  }

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @return a new AlarmService instance
   */
  def apply(asName: String = defaultName)(implicit system: ActorRefFactory, timeout: Timeout): Future[AlarmService] = {
    import system.dispatcher
    for {
      redisClient ← locateAlarmService(asName)
      ok ← redisClient.configSet("notify-keyspace-events", "KEA")
    } yield {
      if (!ok) logger.error("redis configSet notify-keyspace-events failed")
      AlarmServiceImpl(redisClient)
    }
  }

  /**
   * Describes any validation problems found
   *
   * @param severity a string describing the error severity: fatal, error, warning, etc.
   * @param message  describes the problem
   */
  case class Problem(severity: String, message: String) {
    def errorMessage(): String = s"$severity: $message"

    override def toString: String = errorMessage()
  }

  object Problem {
    def errorCount(problems: List[Problem]): Int = {
      problems.count(p ⇒ p.severity == "error" || p.severity == "fatal")
    }

    def printProblems(problems: List[Problem]): List[Problem] = {
      for (problem ← problems) {
        println(s"${problem.severity}: ${problem.message}")
      }
      problems
    }
  }

  /**
   * Type of return value from the monitorAlarms method
   */
  trait AlarmMonitor {
    /**
     * Stops the monitoring of the alarms
     */
    def stop(): Unit
  }

  private[alarms] case class AlarmMonitorImpl(actorRef: ActorRef) extends AlarmMonitor {
    override def stop(): Unit = {
      actorRef ! PoisonPill
    }
  }

  /**
   * Returns a string with the contents of the given config, converted to JSON.
   *
   * @param config the config to convert
   * @return the config contents in JSON format
   */
  private def toJson(config: Config): String = {
    config.root.render(jsonOptions)
  }

  // Adds a custom URI scheme, so that config:/... loads the config file as a resource
  // and converts it to JSON. In this way you can use "$ref": "config:/myfile.conf"
  // to refer to external JSON schemas in HOCON format.
  private case object ConfigDownloader extends URIDownloader {
    override def fetch(uri: URI): InputStream = {
      val config = ConfigFactory.parseResources(uri.getPath.substring(1))
      if (config == null) throw new IOException(s"Resource not found: ${uri.getPath}")
      new ByteArrayInputStream(toJson(config).getBytes)
    }
  }

  /**
   * Validates the given Alarm Service config file against the JSON schema
   *
   * @param inputFile the file to validate
   * @return a list of problems, if any were found
   */
  def validate(inputFile: File): List[Problem] = {
    val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
    val jsonSchema = ConfigFactory.parseResources("alarms-schema.conf")
    validate(inputConfig, jsonSchema, inputFile.getName)
  }

  /**
   * Validates the given input config using the given schema config.
   *
   * @param inputConfig   the config to be validated against the schema
   * @param schemaConfig  a config using the JSON schema syntax (but may be simplified to HOCON format)
   * @param inputFileName the name of the original input file (for error messages)
   * @return a list of problems, if any were found
   */
  def validate(inputConfig: Config, schemaConfig: Config, inputFileName: String): List[Problem] = {
    val jsonSchema = JsonLoader.fromString(toJson(schemaConfig))
    val schema = factory.getJsonSchema(jsonSchema)
    val jsonInput = JsonLoader.fromString(toJson(inputConfig))
    validate(schema, jsonInput, inputFileName)
  }

  // Runs the validation and handles any internal exceptions
  // 'source' is the name of the input file for use in error messages.
  private def validate(schema: JsonSchema, jsonInput: JsonNode, source: String): List[Problem] = {
    try {
      validateResult(schema.validate(jsonInput, true), source)
    } catch {
      case e: Exception ⇒
        e.printStackTrace()
        List(Problem("fatal", e.toString))
    }
  }

  // Packages the validation results for return to caller.
  // 'source' is the name of the input file for use in error messages.
  private def validateResult(report: ProcessingReport, source: String): List[Problem] = {
    import scala.collection.JavaConverters._
    val result = for (msg ← report.asScala)
      yield Problem(msg.getLogLevel.toString, formatMsg(msg, source))
    result.toList
  }

  // Formats the error message for display to user.
  // 'source' is the name of the original input file.
  private def formatMsg(msg: ProcessingMessage, source: String): String = {
    import scala.collection.JavaConversions._
    val file = new File(source).getPath

    // try to get a nicely formatted error message that includes the necessary info
    val json = msg.asJson()
    val pointer = json.get("instance").get("pointer").asText()
    val loc = if (pointer.isEmpty) s"$file" else s"$file, at path: $pointer"
    val schemaUri = json.get("schema").get("loadingURI").asText()
    val schemaPointer = json.get("schema").get("pointer").asText()
    val schemaStr = if (schemaUri == "#") "" else s" (schema: $schemaUri:$schemaPointer)"

    // try to get additional messages from the reports section
    val reports = json.get("reports")
    val messages = if (reports == null) ""
    else {
      for (r ← reports.elements().toList) yield r
      val msgElems = (for (r ← reports) yield r.elements().toList).flatten
      val msgTexts = for (e ← msgElems) yield e.get("message").asText()
      "\n" + msgTexts.mkString("\n")
    }

    s"$loc: ${msg.getLogLevel.toString}: ${msg.getMessage}$schemaStr$messages"
  }
}

/**
 * Defines the public API to the Alarm Service
 */
trait AlarmService {

  import AlarmService._

  /**
   * Initialize the alarm data in the Redis instance using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @return a future list of problems that occurred while validating the config file or ingesting the data into Redis
   */
  def initAlarms(inputFile: File): Future[List[Problem]]

  /**
   * Gets the alarm information from Redis for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a future sequence of alarm model objects
   */
  def getAlarms(alarmKey: AlarmKey): Future[Seq[AlarmModel]]

  /**
   * Gets the alarm information from Redis for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm model object
   */
  def getAlarm(key: AlarmKey): Future[AlarmModel]

  /**
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey        the key for the alarm
   * @param severity        the new value of the severity
   * @param alarmExpireSecs number of seconds before an alarm expires (default: 15)
   * @return a future indicating when the operation has completed
   */
  def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel, alarmExpireSecs: Int = defaultExpireSecs): Future[Unit]

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a future severity level result
   */
  def getSeverity(alarmKey: AlarmKey): Future[SeverityLevel]

  /**
   * Starts monitoring the severity levels of the alarm(s) matching by the given key
   *
   * @param alarmKey      the key for the alarm
   * @param subscriberOpt if defined, an actor that will receive an AlarmStatus message whenever the severity of an alarm changes
   * @param notifyOpt     if defined, a function that will be called with an AlarmStatus object whenever the severity of an alarm changes
   * @return an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorAlarms(alarmKey: AlarmKey, subscriberOpt: Option[ActorRef] = None, notifyOpt: Option[AlarmStatus ⇒ Unit] = None): AlarmMonitor

  /**
   * Shuts down the Redis server (For use in test cases that started Redis themselves)
   */
  def shutdown(): Unit
}

/**
 * Provides methods for working with the Alarm Service database.
 *
 * @param redisClient used to access the Redis instance used by the Alarm Service
 */
private[alarms] case class AlarmServiceImpl(redisClient: RedisClient)(implicit system: ActorRefFactory, timeout: Timeout)
    extends AlarmService with ByteStringSerializerLowPriority {

  import AlarmService._

  import system.dispatcher

  override def initAlarms(inputFile: File): Future[List[Problem]] = {
    import net.ceedubs.ficus.Ficus._
    // Use JSON schema to validate the file
    val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
    val jsonSchema = ConfigFactory.parseResources("alarms-schema.conf")
    val problems = AlarmService.validate(inputConfig, jsonSchema, inputFile.getName)
    if (Problem.errorCount(problems) != 0) {
      Future.successful(problems)
    } else {
      val alarmConfigs = inputConfig.as[List[Config]]("alarms")
      val alarms = alarmConfigs.map(AlarmModel(_))

      val f = alarms.map { alarm ⇒
        logger.info(s"Adding alarm: subsystem: ${alarm.subsystem}, component: ${alarm.component}, ${alarm.name}")
        for {
          _ ← setSeverity(AlarmKey(alarm.subsystem, alarm.component, alarm.name), SeverityLevel.Indeterminate)
          result ← redisClient.hmset(AlarmKey(alarm).key, alarm.map())
        } yield result
      }
      Future.sequence(f).map { results ⇒
        if (results.contains(false)) Problem("error", "Failed to initialize alarms") :: problems else problems
      }
    }
  }

  override def getAlarms(alarmKey: AlarmKey): Future[Seq[AlarmModel]] = {
    val pattern = alarmKey.key
    redisClient.keys(pattern).flatMap { keys ⇒
      val f2 = keys.map(getAlarm)
      Future.sequence(f2)
    }
  }

  override def getAlarm(key: AlarmKey): Future[AlarmModel] = {
    getAlarm(key.key)
  }

  /**
   * Gets the alarm object matching the given key from Redis
   *
   * @param key the key for the alarm in redis
   * @return the alarm model object
   */
  private[alarms] def getAlarm(key: String): Future[AlarmModel] = {
    // XXX could cache this!
    redisClient.hgetall(key).map { map ⇒
      if (map.isEmpty) throw new RuntimeException(s"Date for $key not found.")
      AlarmModel(map)
    }
  }

  override def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel, alarmExpireSecs: Int = defaultExpireSecs): Future[Unit] = {
    val key = alarmKey.severityKey
    logger.debug(s"Setting severity for $alarmKey to $severity")
    for {
      result ← redisClient.set(key, severity.toString, exSeconds = Some(alarmExpireSecs)).map(_ ⇒ ())
    } yield result
  }

  override def getSeverity(alarmKey: AlarmKey): Future[SeverityLevel] = {
    val key = alarmKey.severityKey
    val f = redisClient.exists(key).flatMap { exists ⇒
      // If the key exists in Redis, get the severity string and convert it to a SeverityLevel, otherwise
      // assume Indeterminate
      if (exists)
        redisClient.get[String](key).map(_.flatMap(SeverityLevel(_)))
      else
        Future.successful(Some(SeverityLevel.Indeterminate))
    }
    // The option should only be empty below if the severity string was wrong
    f.map(_.getOrElse(SeverityLevel.Indeterminate))
  }

  override def monitorAlarms(alarmKey: AlarmKey, subscriberOpt: Option[ActorRef] = None, notifyOpt: Option[AlarmStatus ⇒ Unit] = None): AlarmMonitor = {
    val actorRef = system.actorOf(AlarmSubscriberActor.props(this, List(alarmKey), subscriberOpt, notifyOpt)
      .withDispatcher("rediscala.rediscala-client-worker-dispatcher"))
    AlarmMonitorImpl(actorRef)
  }

  override def shutdown(): Unit = {
    val f = redisClient.shutdown()
    redisClient.stop()
    Await.ready(f, timeout.duration)
  }
}
