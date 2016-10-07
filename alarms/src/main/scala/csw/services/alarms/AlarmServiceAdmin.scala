package csw.services.alarms

import java.io._

import akka.actor.ActorRefFactory
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.alarms.AlarmModel.SeverityLevel
import csw.services.alarms.AscfValidation.Problem
import csw.services.trackLocation.TrackLocation
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

/**
 * Admin API for the alarm service.
 */
object AlarmServiceAdmin {

  /**
   * Starts a redis instance on a random free port (redis-server must be in your shell path)
   * and registers it with the location service.
   * This is the equivalent of running this from the command line:
   *   {{{
   *   tracklocation --name "Alarm Service Test" --command "redis-server --port %port" --no-exit
   *   }}}
   *
   * This method is mainly for use by tests. In production, you would use the tracklocation app
   * to start Redis once.
   *
   * @param name The name to use to register the alarm service with the location service
   * @param noExit if true, do not exit the application when redis exists
   * @return a future that completes when the redis server exits
   */
  def startAlarmService(name: String = "Alarm Service", noExit: Boolean = true)(implicit ec: ExecutionContext): Future[Unit] = {
    val ne = if (noExit) List("--no-exit") else Nil
    val args = List("--name", name, "--command", "redis-server --port %port") ++ ne
    Future {
      TrackLocation.main(args.toArray)
    }
  }

  def apply(alarmService: AlarmService)(implicit system: ActorRefFactory): AlarmServiceAdmin =
    AlarmServiceAdminImpl(alarmService)
}

/**
 * Defines the admin API for Alarm Service
 */
trait AlarmServiceAdmin {

  /**
   * Initialize the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a future list of problems that occurred while validating the config file or ingesting the data into the database
   */
  def initAlarms(inputFile: File, reset: Boolean = false): Future[List[Problem]]

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  def shutdown(): Future[Unit]
}

/**
 * Provides admin level methods for working with the Alarm Service database.
 *
 * @param alarmService used to set the inial alarm severity
 */
private[alarms] case class AlarmServiceAdminImpl(alarmService: AlarmService)(implicit system: ActorRefFactory)
    extends AlarmServiceAdmin {

  import system.dispatcher

  private val logger = Logger(LoggerFactory.getLogger(getClass))
  val redisClient = alarmService.asInstanceOf[AlarmServiceImpl].redisClient

  // If reset is true, deletes all alarm data in Redis. (Note: DEL does not take wildcards!)
  private def checkReset(reset: Boolean): Future[Unit] = {
    def deleteKeys(keys: Seq[String]): Future[Unit] = {
      if (keys.nonEmpty) redisClient.del(keys: _*).map(_ => ()) else Future.successful(())
    }

    if (reset) {
      val pattern1 = s"${AlarmKey.alarmKeyPrefix}*"
      val pattern2 = s"${AlarmKey.severityKeyPrefix}*"
      val pattern3 = s"${AlarmKey.alarmStateKeyPrefix}*"
      val f1 = redisClient.keys(pattern1).flatMap(deleteKeys)
      val f2 = redisClient.keys(pattern2).flatMap(deleteKeys)
      val f3 = redisClient.keys(pattern3).flatMap(deleteKeys)
      Future.sequence(List(f1, f2, f3)).map(_ => ())
    } else Future.successful(())
  }

  override def initAlarms(inputFile: File, reset: Boolean): Future[List[Problem]] = {
    import net.ceedubs.ficus.Ficus._
    // Use JSON schema to validate the file
    val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
    val jsonSchema = ConfigFactory.parseResources("alarms-schema.conf")
    val problems = AscfValidation.validate(inputConfig, jsonSchema, inputFile.getName)
    if (Problem.errorCount(problems) != 0) {
      Future.successful(problems)
    } else {
      val alarmConfigs = inputConfig.as[List[Config]]("alarms")
      val alarms = alarmConfigs.map(AlarmModel(_))
      // Reset the db if requested, then initialize the alarm db (Nil means return No problems...)
      checkReset(reset).flatMap(_ => initAlarms(alarms).map(_ => Nil))
    }
  }

  // Initialize the Redis db with the given list of alarms
  private def initAlarms(alarms: List[AlarmModel]): Future[Unit] = {
    val fList = alarms.map { alarm =>
      val alarmKey = AlarmKey(alarm)
      logger.debug(s"Adding alarm: subsystem: ${alarm.subsystem}, component: ${alarm.component}, ${alarm.name}")
      // store the static alarm data, alarm state, and the initial severity in redis
      for {
        _ <- redisClient.hmset(alarmKey.key, alarm.asMap())
        _ <- redisClient.hmset(alarmKey.stateKey, AlarmState().asMap())
        _ <- alarmService.setSeverity(alarmKey, SeverityLevel.Disconnected)
      } yield ()
    }
    Future.sequence(fList).map(_ => ())
  }

  override def shutdown(): Future[Unit] = {
    val f = redisClient.shutdown()
    redisClient.stop()
    f.map(_ => ()).recover { case _ => () }
  }
}
