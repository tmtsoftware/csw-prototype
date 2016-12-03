package csw.services.alarms

import java.io._

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import csw.services.alarms.AlarmModel.{AlarmStatus, CurrentSeverity, Health, HealthStatus}
import csw.services.alarms.AlarmService.AlarmMonitor
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem

import scala.concurrent.Await

/**
 * A Blocking API to the alarm service admin methods
 */
object BlockingAlarmServiceAdmin {

  def apply(alarmService: BlockingAlarmService)(implicit system: ActorRefFactory, timeout: Timeout): BlockingAlarmServiceAdmin =
    BlockingAlarmServiceAdmin(AlarmServiceAdmin(alarmService.alarmService))

  def apply(alarmService: AlarmService)(implicit system: ActorRefFactory, timeout: Timeout): BlockingAlarmServiceAdmin =
    BlockingAlarmServiceAdmin(AlarmServiceAdmin(alarmService))
}

/**
 * A Blocking API to the alarm service admin methods
 */
case class BlockingAlarmServiceAdmin(alarmAdmin: AlarmServiceAdmin)(implicit system: ActorRefFactory, timeout: Timeout) {

  /**
   * Initialize the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a list of problems that occurred while validating the config file or ingesting the data into the database
   */
  def initAlarms(inputFile: File, reset: Boolean = false): List[Problem] =
    Await.result(alarmAdmin.initAlarms(inputFile, reset), timeout.duration)

  /**
   * Gets the alarm information from the database for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a sequence of alarm model objects
   */
  def getAlarms(alarmKey: AlarmKey): Seq[AlarmModel] =
    Await.result(alarmAdmin.getAlarms(alarmKey), timeout.duration)

  /**
   * Gets the alarm information from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return an alarm model object
   */
  def getAlarm(key: AlarmKey): AlarmModel = Await.result(alarmAdmin.getAlarm(key), timeout.duration)

  /**
   * Gets the alarm state from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return an alarm state object
   */
  def getAlarmState(key: AlarmKey): AlarmState = Await.result(alarmAdmin.getAlarmState(key), timeout.duration)

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a severity level result
   */
  def getSeverity(alarmKey: AlarmKey): CurrentSeverity =
    Await.result(alarmAdmin.getSeverity(alarmKey), timeout.duration)

  /**
   * Acknowledges the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  def acknowledgeAlarm(alarmKey: AlarmKey): Unit =
    Await.result(alarmAdmin.acknowledgeAlarm(alarmKey), timeout.duration)

  /**
   * Resets the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  def resetAlarm(alarmKey: AlarmKey): Unit =
    Await.result(alarmAdmin.resetAlarm(alarmKey), timeout.duration)

  /**
   * Acknowledges the given alarm, clearing the acknowledged and latched states, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  def acknowledgeAndResetAlarm(alarmKey: AlarmKey): Unit =
    Await.result(alarmAdmin.acknowledgeAndResetAlarm(alarmKey), timeout.duration)

  /**
   * Sets the shelved state of the alarm
   *
   * @param alarmKey     the key for the alarm
   * @param shelvedState the shelved state
   */
  def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Unit =
    Await.result(alarmAdmin.setShelvedState(alarmKey, shelvedState), timeout.duration)

  /**
   * Sets the activation state of the alarm
   *
   * @param alarmKey        the key for the alarm
   * @param activationState the activation state
   */
  def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Unit =
    Await.result(alarmAdmin.setActivationState(alarmKey, activationState), timeout.duration)

  /**
   * Gets the health of the system, subsystem or component, based on the given alarm key.
   *
   * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @return the health value (good, ill, bad)
   */
  def getHealth(alarmKey: AlarmKey): Health =
    Await.result(alarmAdmin.getHealth(alarmKey), timeout.duration)

  /**
   * Starts monitoring the health of the system, subsystem or component
   *
   * @param alarmKey     an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                     that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @param subscriber   an actor that will receive a HealthStatus message whenever the health for the given key changes
   * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
   *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                     and where the latched severity or calculated health actually changed
   * @return an object containing an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorAlarms(alarmKey: AlarmKey, subscriber: ActorRef, notifyAll: Boolean): AlarmMonitor = alarmAdmin.monitorAlarms(alarmKey, subscriber, notifyAll)

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
   * @return an object containing an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorAlarms(alarmKey: AlarmKey, notifyAlarm: AlarmStatus => Unit, notifyHealth: HealthStatus => Unit, notifyAll: Boolean): AlarmMonitor =
    alarmAdmin.monitorAlarms(alarmKey, notifyAlarm, notifyHealth, notifyAll)

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  //  def shutdown(): Unit = Await.result(alarmAdmin.shutdown(), timeout.duration)
  def shutdown(): Unit = alarmAdmin.shutdown()
}
