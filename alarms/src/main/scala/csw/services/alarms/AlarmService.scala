package csw.services.alarms

import java.io._

import akka.actor.{ActorRef, ActorRefFactory, PoisonPill}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.alarms.AlarmModel.{AlarmStatus, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmState.{AcknowledgedState, ActivationState, LatchedState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem
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
  private[alarms] val logger = Logger(LoggerFactory.getLogger("AlarmService"))

  /**
   * The default name that the Alarm Service is registered with
   */
  val defaultName = "Alarm Service"

  /**
   * An alarm's severity should be refreshed every defaultRefreshSecs seconds
   * to make sure it does not expire and become "Disconnected" (after maxMissedRefresh missed refreshes)
   */
  val defaultRefreshSecs = 5

  /**
   * The default number of refreshes that may be missed before an alarm's severity is expired
   * and becomes "Disconnected"
   */
  val maxMissedRefresh = 3

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
   * @param asName      name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param refreshSecs alarm severity should be reset every refreshSecs seconds to avoid being expired and set
   *                    to "Disconnected" (after three missed refreshes)
   * @return a new AlarmService instance
   */
  def apply(asName: String = defaultName, refreshSecs: Int = defaultRefreshSecs)(implicit system: ActorRefFactory, timeout: Timeout): Future[AlarmService] = {
    import system.dispatcher
    for {
      redisClient ← locateAlarmService(asName)
      ok ← redisClient.configSet("notify-keyspace-events", "KEA")
    } yield {
      if (!ok) logger.error("redis configSet notify-keyspace-events failed")
      AlarmServiceImpl(redisClient, refreshSecs)
    }
  }

  /**
   * Type of return value from the monitorAlarms or monitorHealth methods
   */
  trait AlarmMonitor {
    /**
     * Stops the monitoring actor
     */
    def stop(): Unit

    /**
     * A reference to the monitoring actor (could be used to watch the actor to detect if it stops for some reason)
     *
     * @return
     */
    def actorRef: ActorRef
  }

  private[alarms] case class AlarmMonitorImpl(actorRef: ActorRef) extends AlarmMonitor {
    override def stop(): Unit = {
      actorRef ! PoisonPill
    }
  }

  // Information needed about each alarm in order to calculate the health
  private[alarms] case class HealthInfo(alarmKey: AlarmKey, severityLevel: SeverityLevel, alarmState: AlarmState)

}

/**
 * Defines the public API to the Alarm Service
 */
trait AlarmService {

  import AlarmService._

  /**
   * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
   */
  def refreshSecs: Int

  /**
   * Initialize the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a future list of problems that occurred while validating the config file or ingesting the data into the database
   */
  def initAlarms(inputFile: File, reset: Boolean = false): Future[List[Problem]]

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
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @param severity the new value of the severity
   * @return a future indicating when the operation has completed
   */
  def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): Future[Unit]

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a future severity level result
   */
  def getSeverity(alarmKey: AlarmKey): Future[SeverityLevel]

  /**
   * Acknowledges the given alarm, clearing the acknowledged and latched states, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  def acknowledgeAlarm(alarmKey: AlarmKey): Future[Unit]

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
   * @param subscriber   if defined, an actor that will receive a HealthStatus message whenever the health for the given key changes
   * @param notifyAlarm  if defined, a function that will be called with an AlarmStatus object whenever the severity of an alarm changes
   * @param notifyHealth if defined, a function that will be called with a HealthStatus object whenever the total health for key pattern changes
   * @return an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorHealth(
    alarmKey:     AlarmKey,
    subscriber:   Option[ActorRef]            = None,
    notifyAlarm:  Option[AlarmStatus ⇒ Unit]  = None,
    notifyHealth: Option[HealthStatus ⇒ Unit] = None
  ): AlarmMonitor

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  def shutdown(): Unit
}

/**
 * Provides methods for working with the Alarm Service database.
 *
 * @param redisClient used to access the Redis instance used by the Alarm Service
 * @param refreshSecs alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
 */
private[alarms] case class AlarmServiceImpl(redisClient: RedisClient, refreshSecs: Int)(implicit system: ActorRefFactory, timeout: Timeout)
    extends AlarmService with ByteStringSerializerLowPriority {

  import AlarmService._
  import system.dispatcher

  // If reset is true, deletes all alarm data in Redis. (Note: DEL does not take wildcards!)
  private def checkReset(reset: Boolean): Future[Unit] = {
    def deleteKeys(keys: Seq[String]): Future[Unit] = {
      if (keys.nonEmpty) redisClient.del(keys: _*).map(_ ⇒ ()) else Future.successful(())
    }

    if (reset) {
      val pattern1 = s"${AlarmKey.alarmKeyPrefix}*"
      val pattern2 = s"${AlarmKey.severityKeyPrefix}*"
      val pattern3 = s"${AlarmKey.alarmStateKeyPrefix}*"
      val f1 = redisClient.keys(pattern1).flatMap(deleteKeys)
      val f2 = redisClient.keys(pattern2).flatMap(deleteKeys)
      val f3 = redisClient.keys(pattern3).flatMap(deleteKeys)
      Future.sequence(List(f1, f2, f3)).map(_ ⇒ ())
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
      checkReset(reset).flatMap(_ ⇒ initAlarms(alarms).map(_ ⇒ Nil))
    }
  }

  // Initialize the Redis db with the given list of alarms
  private def initAlarms(alarms: List[AlarmModel]): Future[Unit] = {
    val fList = alarms.map { alarm ⇒
      val alarmKey = AlarmKey(alarm)
      logger.info(s"Adding alarm: subsystem: ${alarm.subsystem}, component: ${alarm.component}, ${alarm.name}")
      // store the static alarm data, alarm state, and the initial severity in redis
      for {
        _ ← redisClient.hmset(alarmKey.key, alarm.asMap())
        _ ← redisClient.hmset(alarmKey.stateKey, AlarmState().asMap())
        _ ← setSeverity(alarmKey, SeverityLevel.Disconnected) // XXX could simplify this on init and do simple set?
      } yield ()
    }
    Future.sequence(fList).map(_ ⇒ ())
  }

  override def getAlarms(alarmKey: AlarmKey): Future[Seq[AlarmModel]] = {
    val pattern = alarmKey.key
    redisClient.keys(pattern).flatMap { keys ⇒
      Future.sequence(keys.map(getAlarm)).map(_.flatten)
    }
  }

  override def getAlarm(key: AlarmKey): Future[AlarmModel] = {
    getAlarm(key.key).map { opt ⇒
      if (opt.isEmpty) {
        throw new RuntimeException(s"No alarm was found for key $key")
      } else opt.get
    }
  }

  // Gets the abbreviated alarm model for the given key
  private def getAlarmSmall(key: AlarmKey): Future[AlarmModelSmall] = {
    redisClient.hmget(key.key, "severityLevels", "acknowledge", "latched").map(AlarmModelSmall(_)).map { opt ⇒
      if (opt.isEmpty) {
        throw new RuntimeException(s"No alarm was found for key $key")
      } else opt.get
    }
  }

  /**
   * Gets the alarm object matching the given key from Redis
   *
   * @param key the key for the alarm in redis
   * @return the alarm model object, if found
   */
  private[alarms] def getAlarm(key: String): Future[Option[AlarmModel]] = {
    redisClient.hgetall(key).map(AlarmModel(_))
  }

  override def getAlarmState(key: AlarmKey): Future[AlarmState] = {
    redisClient.hgetall(key.stateKey).map { map ⇒
      if (map.isEmpty) throw new RuntimeException(s"Alarm state for $key not found.")
      AlarmState(map)
    }
  }

  override def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): Future[Unit] = {
    for {
      alarm ← getAlarmSmall(alarmKey)
      alarmState ← getAlarmState(alarmKey)
      currentSeverity ← getSeverity(alarmKey)
      result ← setSeverity(alarmKey, alarm, alarmState, severity, currentSeverity)
    } yield result
  }

  // Sets the severity of the alarm, if allowed based on the alarm state
  private def setSeverity(alarmKey: AlarmKey, alarm: AlarmModelSmall, alarmState: AlarmState,
                          severity: SeverityLevel, currentSeverity: SeverityLevel): Future[Unit] = {

    // Check that the severity is listed in the spec (XXX Should this throw an exception?)
    if (severity != SeverityLevel.Disconnected && !alarm.severityLevels.contains(severity))
      logger.warn(s"Alarm $alarmKey is not listed as supporting severity level $severity")

    // Is the alarm latched?
    val newSeverity = if (alarm.latched) {
      if (alarmState.latchedState == LatchedState.Normal) severity
      else {
        if (severity.level > currentSeverity.level) severity
        else currentSeverity
      }
    } else severity

    // Do we need to update the latched state or the acknowledge state?
    val updateLatchedState = alarm.latched && severity.isAlarm && alarmState.latchedState == LatchedState.Normal
    val updateAckState = alarm.acknowledge && severity.isAlarm && alarmState.acknowledgedState == AcknowledgedState.Normal

    val redisTransaction = redisClient.transaction()
    logger.debug(s"Setting severity for $alarmKey to $newSeverity")

    val f1 = redisTransaction.set(alarmKey.severityKey, newSeverity.name, exSeconds = Some(refreshSecs * maxMissedRefresh))

    val f2 = if (updateLatchedState) {
      logger.debug(s"Setting latched state for $alarmKey to NeedsReset")
      Future.sequence(List(
        redisTransaction.hset(alarmKey.stateKey, AlarmState.latchedStateField, LatchedState.NeedsReset.name),
        redisTransaction.hset(alarmKey.stateKey, AlarmState.latchedSeverityField, newSeverity.name)
      ))
    } else Future.successful(true)

    val f3 = if (updateAckState) {
      logger.debug(s"Setting acknowledged state for $alarmKey to NeedsAcknowledge")
      redisTransaction.hset(alarmKey.stateKey, AlarmState.acknowledgedStateField, AcknowledgedState.NeedsAcknowledge.name)
    } else Future.successful(true)

    val f4 = redisTransaction.exec()

    Future.sequence(List(f1, f2, f3, f4)).map(_ ⇒ ())
  }

  override def getSeverity(alarmKey: AlarmKey): Future[SeverityLevel] = {
    val key = alarmKey.severityKey
    val f = redisClient.exists(key).flatMap { exists ⇒
      // If the key exists in Redis, get the severity string and convert it to a SeverityLevel, otherwise
      // assume Disconnected
      if (exists)
        redisClient.get[String](key).map(_.flatMap(SeverityLevel(_)))
      else {
        // If the key doesn't exist, check if it is latched
        getAlarmState(alarmKey).map { alarmState ⇒
          if (alarmState.latchedState == LatchedState.Normal)
            Some(SeverityLevel.Disconnected)
          else
            Some(alarmState.latchedSeverity)
        }
      }
    }
    // The option should only be empty below if the severity string was wrong
    f.map(_.getOrElse(SeverityLevel.Disconnected))
  }

  override def acknowledgeAlarm(alarmKey: AlarmKey): Future[Unit] = {
    for {
      alarmState ← getAlarmState(alarmKey)
      currentSeverity ← getSeverity(alarmKey)
      result ← acknowledgeAlarm(alarmKey, alarmState)
    } yield result
  }

  // acknowledge the given alarm and update the alarm state and severity if needed
  private def acknowledgeAlarm(alarmKey: AlarmKey, alarmState: AlarmState): Future[Unit] = {
    val redisTransaction = redisClient.transaction()

    val f1 = if (alarmState.acknowledgedState == AcknowledgedState.NeedsAcknowledge) {
      logger.debug(s"Acknowledging alarm: $alarmKey and resetting to Okay")
      Future.sequence(List(
        redisTransaction.hset(alarmKey.stateKey, AlarmState.acknowledgedStateField, AcknowledgedState.Normal.name),
        redisTransaction.set(alarmKey.severityKey, SeverityLevel.Okay.name, exSeconds = Some(refreshSecs * maxMissedRefresh))
      ))
    } else Future.successful(true)

    val f2 = if (alarmState.latchedState == LatchedState.NeedsReset) {
      logger.debug(s"Resetting latched state for alarm: $alarmKey")
      redisTransaction.hset(alarmKey.stateKey, AlarmState.latchedStateField, LatchedState.Normal.name)
      redisTransaction.hset(alarmKey.stateKey, AlarmState.latchedSeverityField, SeverityLevel.Okay.name)
    } else Future.successful(true)

    val f3 = redisTransaction.exec()

    Future.sequence(List(f1, f2, f3)).map(_ ⇒ ())
  }

  override def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Future[Unit] = {
    for {
      exists ← redisClient.exists(alarmKey.stateKey)
      if exists
      result ← redisClient.hset(alarmKey.stateKey, AlarmState.shelvedStateField, shelvedState.name).map(_ ⇒ ())
    } yield {
      if (!exists) throw new RuntimeException(s"Can't set shelved state for unknown key: $alarmKey")
      result
    }
  }

  override def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Future[Unit] = {

    for {
      exists ← redisClient.exists(alarmKey.stateKey)
      if exists
      result ← redisClient.hset(alarmKey.stateKey, AlarmState.activationStateField, activationState.name).map(_ ⇒ ())
    } yield {
      if (!exists) throw new RuntimeException(s"Can't set activation state for unknown key: $alarmKey")
      result
    }
  }

  override def getHealth(alarmKey: AlarmKey): Future[Health] = {
    getHealthInfoMap(alarmKey).map(getHealth)
  }

  // Returns a future map from alarm key to health info for each alarm key matching the given alarm key pattern
  private[alarms] def getHealthInfoMap(alarmKey: AlarmKey): Future[Map[AlarmKey, HealthInfo]] = {
    val pattern = alarmKey.key
    redisClient.keys(pattern).map(_.map(AlarmKey(_))).flatMap { alarmKeys ⇒
      if (alarmKeys.isEmpty) throw new RuntimeException("Can't get health information: No alarms matched $alarmKey")
      val fs = alarmKeys.map { k ⇒
        for {
          sev ← getSeverity(k)
          state ← getAlarmState(k)
        } yield {
          k → HealthInfo(k, sev, state)
        }
      }
      Future.sequence(fs).map(_.toMap)
    }
  }

  // Gets the health value, given a map with the alarm severity and state for all the relevant alarms.
  // Health is good if a component’s alarms all have Normal or Warning severity. If the component has at
  // least one alarm with Major severity, it’s health is Ill. If the component has at least one alarm with
  // Critical severity, it’s health is Bad. Additionally, if the component’s severity is Disconnected or
  // Indeterminate, it’s health is also Bad.
  private[alarms] def getHealth(alarmMap: Map[AlarmKey, HealthInfo]): Health = {
    def active(h: HealthInfo): Boolean = h.alarmState.shelvedState == ShelvedState.Normal && h.alarmState.activationState == ActivationState.Normal
    val severityLevels = alarmMap.values.filter(active).map(_.severityLevel).toList

    if (severityLevels.contains(SeverityLevel.Critical) || severityLevels.contains(SeverityLevel.Disconnected) || severityLevels.contains(SeverityLevel.Indeterminate))
      Health.Bad
    else if (severityLevels.contains(SeverityLevel.Major))
      Health.Ill
    else Health.Good
  }

  override def monitorHealth(
    alarmKey:      AlarmKey,
    subscriberOpt: Option[ActorRef]            = None,
    notifyAlarm:   Option[AlarmStatus ⇒ Unit]  = None,
    notifyOpt:     Option[HealthStatus ⇒ Unit] = None
  ): AlarmMonitor = {
    val actorRef = system.actorOf(HealthMonitorActor.props(this, alarmKey, subscriberOpt, notifyAlarm, notifyOpt)
      .withDispatcher("rediscala.rediscala-client-worker-dispatcher"))
    AlarmMonitorImpl(actorRef)
  }

  override def shutdown(): Unit = {
    val f = redisClient.shutdown()
    redisClient.stop()
    Await.ready(f, timeout.duration)
  }
}
