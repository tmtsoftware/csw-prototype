package csw.services.alarms

import java.io.File

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import csw.services.alarms.AlarmModel.{AlarmStatus, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem
import AlarmService._

import scala.concurrent.Await

/**
 * A convenience wrapper for [[AlarmService]] that blocks waiting for future return values.
 */
object BlockingAlarmService {
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
   * @return a new BlockingAlarmService instance
   */
  def apply(asName: String = defaultName, refreshSecs: Int = defaultRefreshSecs)(implicit system: ActorRefFactory, timeout: Timeout): BlockingAlarmService = {
    BlockingAlarmService(Await.result(AlarmService(asName, refreshSecs), timeout.duration))
  }
}

/**
 * A convenience wrapper for [[AlarmService]] that blocks waiting for future return values.
 */
case class BlockingAlarmService(alarmService: AlarmService)(implicit val timeout: Timeout, context: ActorRefFactory) {

  /**
   * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
   */
  def refreshSecs: Int = alarmService.refreshSecs

  /**
   * Initialize the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a list of problems that occurred while validating the config file or ingesting the data into the database
   */
  def initAlarms(inputFile: File, reset: Boolean = false): List[Problem] =
    Await.result(alarmService.initAlarms(inputFile, reset), timeout.duration)

  /**
   * Gets the alarm information from the database for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a sequence of alarm model objects
   */
  def getAlarms(alarmKey: AlarmKey): Seq[AlarmModel] =
    Await.result(alarmService.getAlarms(alarmKey), timeout.duration)

  /**
   * Gets the alarm information from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return an alarm model object
   */
  def getAlarm(key: AlarmKey): AlarmModel = Await.result(alarmService.getAlarm(key), timeout.duration)

  /**
   * Gets the alarm state from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return an alarm state object
   */
  def getAlarmState(key: AlarmKey): AlarmState = Await.result(alarmService.getAlarmState(key), timeout.duration)

  /**
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @param severity the new value of the severity
   */
  def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): Unit =
    Await.result(alarmService.setSeverity(alarmKey, severity), timeout.duration)

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a severity level result
   */
  def getSeverity(alarmKey: AlarmKey): SeverityLevel =
    Await.result(alarmService.getSeverity(alarmKey), timeout.duration)

  /**
   * Acknowledges the given alarm, clearing the acknowledged and latched states, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  def acknowledgeAlarm(alarmKey: AlarmKey): Unit =
    Await.result(alarmService.acknowledgeAlarm(alarmKey), timeout.duration)

  /**
   * Sets the shelved state of the alarm
   *
   * @param alarmKey     the key for the alarm
   * @param shelvedState the shelved state
   */
  def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Unit =
    Await.result(alarmService.setShelvedState(alarmKey, shelvedState), timeout.duration)

  /**
   * Sets the activation state of the alarm
   *
   * @param alarmKey        the key for the alarm
   * @param activationState the activation state
   */
  def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Unit =
    Await.result(alarmService.setActivationState(alarmKey, activationState), timeout.duration)

  /**
   * Gets the health of the system, subsystem or component, based on the given alarm key.
   *
   * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @return the health value (good, ill, bad)
   */
  def getHealth(alarmKey: AlarmKey): Health =
    Await.result(alarmService.getHealth(alarmKey), timeout.duration)

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
    subscriber:   Option[ActorRef]             = None,
    notifyAlarm:  Option[AlarmStatus => Unit]  = None,
    notifyHealth: Option[HealthStatus => Unit] = None
  ): AlarmMonitor =
    alarmService.monitorHealth(alarmKey, subscriber, notifyAlarm, notifyHealth)

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  def shutdown(): Unit = alarmService.shutdown()

}
