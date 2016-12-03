package javacsw.services.alarms;

import akka.actor.ActorRef;
import csw.services.alarms.*;

import java.io.File;
import java.util.List;

/**
 * Java blocking admin API for the alarm service.
 */
@SuppressWarnings({"SameParameterValue", "unused"})
public interface IBlockingAlarmAdmin {
  /**
   * Initializes the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a list of problems that occurred while validating the config file or ingesting the data into the database
   */
  List<AscfValidation.Problem> initAlarms(File inputFile, boolean reset);

  /**
   * Gets the alarm information from the database for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a sequence of alarm model objects
   */
  List<AlarmModel> getAlarms(AlarmKey alarmKey);

  /**
   * Gets the alarm information from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return an alarm model object
   */
  AlarmModel getAlarm(AlarmKey key);

  /**
   * Gets the alarm state from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return an alarm state object
   */
  AlarmState getAlarmState(AlarmKey key);

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a severity level result
   */
  AlarmModel.CurrentSeverity getSeverity(AlarmKey alarmKey);

  /**
   * Acknowledges the given alarm, clearing the acknowledged and latched states, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  void acknowledgeAlarm(AlarmKey alarmKey);

  /**
   * Resets the latched state of the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  void resetAlarm(AlarmKey alarmKey);

  /**
   * Acknowledges the given alarm and resets the latched state, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  void acknowledgeAndResetAlarm(AlarmKey alarmKey);

  /**
   * Sets the shelved state of the alarm
   *
   * @param alarmKey     the key for the alarm
   * @param shelvedState the shelved state
   */
  void setShelvedState(AlarmKey alarmKey, AlarmState.ShelvedState shelvedState);

  /**
   * Sets the activation state of the alarm
   *
   * @param alarmKey        the key for the alarm
   * @param activationState the activation state
   */
  void setActivationState(AlarmKey alarmKey, AlarmState.ActivationState activationState);

  /**
   * Gets the health of the system, subsystem or component, based on the given alarm key.
   *
   * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @return the health value (good, ill, bad)
   */
  AlarmModel.Health getHealth(AlarmKey alarmKey);

  /**
   * Starts monitoring the health of the system, subsystem or component
   *
   * @param alarmKey     an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                     that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @param subscriber   an actor that will receive a HealthStatus message whenever the health for the given key changes
   * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
   *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                     and where the latched severity or calculated health actually changed
   * @return an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  AlarmService.AlarmMonitor monitorAlarms(
    AlarmKey alarmKey,
    ActorRef subscriber,
    boolean notifyAll
  );

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
   * @return an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  AlarmService.AlarmMonitor monitorAlarms(
    AlarmKey alarmKey,
    IAlarmServiceAdmin.AlarmHandler notifyAlarm,
    IAlarmServiceAdmin.HealthHandler notifyHealth,
    boolean notifyAll
  );

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  void shutdown();
}
