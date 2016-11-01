package csw.services.alarms

import java.io._

import akka.actor.{ActorRef, ActorRefFactory}
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.alarms.AlarmModel.{AlarmStatus, CurrentSeverity, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem
import csw.services.trackLocation.TrackLocation
import org.slf4j.LoggerFactory
import AlarmService._

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
  def startAlarmService(name: String = AlarmService.defaultName, noExit: Boolean = true)(implicit ec: ExecutionContext): Future[Unit] = {
    val ne = if (noExit) List("--no-exit") else Nil
    val args = List("--name", name, "--command", "redis-server --protected-mode no --port %port") ++ ne
    Future {
      TrackLocation.main(args.toArray)
    }
  }

  def apply(alarmService: AlarmService)(implicit system: ActorRefFactory): AlarmServiceAdmin =
    AlarmServiceAdminImpl(alarmService.asInstanceOf[AlarmServiceImpl])
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
   * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
   */
  def refreshSecs: Int

  /**
   * Gets the alarm information from the database for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a future sequence of alarm model objects
   */
  def getAlarms(alarmKey: AlarmKey): Future[Seq[AlarmModel]]

  /**
   * Gets the alarm information from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm model object
   */
  def getAlarm(key: AlarmKey): Future[AlarmModel]

  /**
   * Gets the alarm state from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm state object
   */
  def getAlarmState(key: AlarmKey): Future[AlarmState]

  /**
   * Gets the severity level for the given alarm
   * (or the latched severity, if the alarm is latched and unacknowledged)
   *
   * @param alarmKey the key for the alarm
   * @return a future severity level result
   */
  def getSeverity(alarmKey: AlarmKey): Future[CurrentSeverity]

  /**
   * Acknowledges the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  def acknowledgeAlarm(alarmKey: AlarmKey): Future[Unit]

  /**
   * Resets the latched state of the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  def resetAlarm(alarmKey: AlarmKey): Future[Unit]

  /**
   * Acknowledges the given alarm and resets the latched state, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  def acknowledgeAndResetAlarm(alarmKey: AlarmKey): Future[Unit]

  /**
   * Sets the shelved state of the alarm
   *
   * @param alarmKey     the key for the alarm
   * @param shelvedState the shelved state
   * @return a future indicating when the operation has completed
   */
  def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Future[Unit]

  /**
   * Sets the activation state of the alarm
   *
   * @param alarmKey        the key for the alarm
   * @param activationState the activation state
   * @return a future indicating when the operation has completed
   */
  def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Future[Unit]

  /**
   * Gets the health of the system, subsystem or component, based on the given alarm key.
   *
   * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @return the future health value (good, ill, bad)
   */
  def getHealth(alarmKey: AlarmKey): Future[Health]

  /**
   * Starts monitoring the health of the system, subsystem or component
   *
   * @param alarmKey     an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                     that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @param subscriber   an actor that will receive a HealthStatus message whenever the health for the given key changes
   * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
   *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                     and where the latched severity or calculated health actually changed
   * @return an object containing the actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorAlarms(alarmKey: AlarmKey, subscriber: ActorRef, notifyAll: Boolean): AlarmMonitor

  /**
   * Starts monitoring the health of the system, subsystem or component
   *
   * @param alarmKey     an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                     that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @param notifyAlarm  a function that will be called with an AlarmStatus object whenever the severity of an alarm changes
   * @param notifyHealth a function that will be called with a HealthStatus object whenever the total health for key pattern changes
   * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
   *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                     and where the latched severity or calculated health actually changed
   * @return an object containing the actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorAlarms(alarmKey: AlarmKey, notifyAlarm: AlarmStatus => Unit, notifyHealth: HealthStatus => Unit, notifyAll: Boolean): AlarmMonitor

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  def shutdown(): Future[Unit]
}

/**
 * Provides admin level methods for working with the Alarm Service database.
 *
 * @param asi alarm service private implementation
 */
private[alarms] case class AlarmServiceAdminImpl(asi: AlarmServiceImpl)(implicit system: ActorRefFactory)
    extends AlarmServiceAdmin {

  import system.dispatcher

  private val logger = Logger(LoggerFactory.getLogger(getClass))
  val redisClient = asi.redisClient

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
        _ <- asi.setSeverity(alarmKey, SeverityLevel.Disconnected)
      } yield ()
    }
    Future.sequence(fList).map(_ => ())
  }

  override def refreshSecs: Int = asi.refreshSecs

  override def getAlarms(alarmKey: AlarmKey): Future[Seq[AlarmModel]] = asi.getAlarms(alarmKey)

  override def getAlarm(key: AlarmKey): Future[AlarmModel] = asi.getAlarm(key)

  override def getAlarmState(key: AlarmKey): Future[AlarmState] = asi.getAlarmState(key)

  override def getSeverity(alarmKey: AlarmKey): Future[CurrentSeverity] = asi.getSeverity(alarmKey)

  override def acknowledgeAlarm(alarmKey: AlarmKey): Future[Unit] = asi.acknowledgeAlarm(alarmKey)

  override def resetAlarm(alarmKey: AlarmKey): Future[Unit] = asi.resetAlarm(alarmKey)

  override def acknowledgeAndResetAlarm(alarmKey: AlarmKey): Future[Unit] = asi.acknowledgeAndResetAlarm(alarmKey)

  override def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Future[Unit] = asi.setShelvedState(alarmKey, shelvedState)

  override def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Future[Unit] = asi.setActivationState(alarmKey, activationState)

  override def getHealth(alarmKey: AlarmKey): Future[Health] = asi.getHealth(alarmKey)

  override def monitorAlarms(alarmKey: AlarmKey, subscriber: ActorRef, notifyAll: Boolean): AlarmMonitor = asi.monitorAlarms(alarmKey, subscriber, notifyAll)

  override def monitorAlarms(alarmKey: AlarmKey, notifyAlarm: (AlarmStatus) => Unit, notifyHealth: (HealthStatus) => Unit, notifyAll: Boolean): AlarmMonitor = asi.monitorAlarms(alarmKey, notifyAlarm, notifyHealth, notifyAll)

  override def shutdown(): Future[Unit] = {
    val f = redisClient.shutdown()
    redisClient.stop()
    f.map(_ => ()).recover { case _ => () }
  }
}
