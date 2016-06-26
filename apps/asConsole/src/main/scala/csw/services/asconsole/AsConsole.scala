package csw.services.asconsole

import java.io.File

import akka.actor.ActorSystem
import akka.util.Timeout
import csw.services.loc.Connection.HttpConnection
import csw.services.loc.LocationService.ResolvedHttpLocation
import csw.services.loc.{ComponentId, ComponentType, LocationService}
import redis.RedisClient

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * A command line application that locates the Redis instance used for the Alarm Service (using the Location Service)
 * and performs tasks based on the command line options, such as initialize or display the list of alarms.
 */
object AsConsole extends App {
  LocationService.initInterface()

  // Needed for use with Futures
  implicit val system = ActorSystem()

  // Needed for (implicit ec: ExecutionContext) args
  import system.dispatcher

  // Timeout when waiting for a future
  implicit val timeout = Timeout(60.seconds)

  /**
   * Command line options ("asconsole --help" prints a usage message with descriptions of all the options)
   * See val parser below for descriptions of the options.
   */
  private case class Options(
    asName:     Option[String] = None,
    ascf:       Option[File]   = None,
    listAlarms: Boolean        = false,
    subsystem:  Option[String] = None,
    component:  Option[String] = None,
    name:       Option[String] = None
  )

  // XXX TODO: Add options for --list output format: pdf, html, json, config, text?

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("asconsole") {
    head("asconsole", System.getProperty("CSW_VERSION"))

    opt[String]("as-name") valueName "<name>" action { (x, c) ⇒
      c.copy(asName = Some(x))
    } text "The name that was used to register the Alarm Service Redis instance (Default: 'Alarm Service')"

    opt[File]("init") valueName "<alarm-service-config-file>" action { (x, c) ⇒
      c.copy(ascf = Some(x))
    } text "Initialize the set of available alarms from the given Alarm Service Config File (ASCF)"

    opt[Unit]("list").action((_, c) ⇒
      c.copy(listAlarms = true)).text("Prints a list of all alarms (See other options to filter what is printed)")

    opt[String]('c', "component") valueName "<name>" action { (x, c) ⇒
      c.copy(component = Some(x))
    } text "Limits the alarms returned by --list to the given component (subsystem must also be specified)"

    opt[String]('s', "subsystem") valueName "<subsystem>" action { (x, c) ⇒
      c.copy(subsystem = Some(x))
    } text "Limits the alarms returned by --list to the given subsystem"

    opt[String]('s', "name") valueName "<name>" action { (x, c) ⇒
      c.copy(name = Some(x))
    } text "Limits the alarms returned by --list to those whose name field matches the given value (may contain Redis wildcards)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) ⇒
      try {
        run(options)
      } catch {
        case e: Throwable ⇒
          e.printStackTrace()
          System.exit(1)
      }
    case None ⇒ System.exit(1)
  }

  // Report error and exit
  private def error(msg: String): Unit = {
    println(msg)
    System.exit(1)
  }

  // Finds the Alarm Service Redis instance and acts on the command line options
  private def run(options: Options): Unit = {
    val asName = options.asName.getOrElse("Alarm Service")

    locateAlarmService(asName) match {
      case Success(redisClient) ⇒ run(redisClient, options)
      case Failure(ex)          ⇒ error(s"Could not locate the alarm service: $ex")
    }
  }

  // Uses the given Alarm Service Redis instance to act on the command line options
  private def run(redisClient: RedisClient, options: Options): Unit = {
    import AlarmUtils.Problem

    options.ascf foreach { file ⇒
      val problems = Await.result(AlarmUtils.initAlarms(redisClient, file), timeout.duration)
      Problem.printProblems(problems)
      if (Problem.errorCount(problems) != 0) System.exit(1)
    }

    if (options.listAlarms) {
      val alarms = Await.result(AlarmUtils.getAlarms(redisClient, options.subsystem, options.component, options.name), timeout.duration)
      alarms.foreach { alarm ⇒
        // XXX TODO: add format options
        println(alarm)
      }
    }
  }

  // Lookup the alarm service redis instance with the location service
  private def locateAlarmService(asName: String = ""): Try[RedisClient] = Try {
    val connection = HttpConnection(ComponentId(asName, ComponentType.Service))
    val locationsReady = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    val uri = locationsReady.locations.head.asInstanceOf[ResolvedHttpLocation].uri
    RedisClient(uri.getHost, uri.getPort)
  }
}

