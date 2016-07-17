package javacsw.services.alarms;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.util.Timeout;
import csw.services.alarms.*;
import csw.services.alarms.AlarmModel.*;
import csw.services.alarms.AlarmService.*;
import csw.services.alarms.AlarmState.*;

import java.io.File;
import java.util.List;
import java.util.Optional;
import javacsw.services.alarms.IAlarmService.*;

/**
 * Defines a synchronous/blocking Java API to the Alarm Service
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public interface IBlockingAlarmService {
  /**
   * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
   */
  int refreshSecs();

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
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @param severity the new value of the severity
   */
  void setSeverity(AlarmKey alarmKey, SeverityLevel severity);

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a severity level result
   */
  CurrentSeverity getSeverity(AlarmKey alarmKey);

  /**
   * Acknowledges the given alarm, clearing the acknowledged and latched states, if needed.
   *
   * @param alarmKey the key for the alarm
   */
  void acknowledgeAlarm(AlarmKey alarmKey);

  /**
   * Sets the shelved state of the alarm
   *
   * @param alarmKey     the key for the alarm
   * @param shelvedState the shelved state
   */
  void setShelvedState(AlarmKey alarmKey, ShelvedState shelvedState);

  /**
   * Sets the activation state of the alarm
   *
   * @param alarmKey        the key for the alarm
   * @param activationState the activation state
   */
  void setActivationState(AlarmKey alarmKey, ActivationState activationState);

  /**
   * Gets the health of the system, subsystem or component, based on the given alarm key.
   *
   * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @return the health value (good, ill, bad)
   */
  Health getHealth(AlarmKey alarmKey);

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
  AlarmMonitor monitorHealth(
    AlarmKey alarmKey,
    Optional<ActorRef> subscriber,
    // XXX TODO: Add code to convert Java void to Scala Unit?
    Optional<AlarmHandler> notifyAlarm,
    Optional<HealthHandler> notifyHealth
  );

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  void shutdown();


  // --- Static factory methods to create an IBlockingAlarmService instance --

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an IBlockingAlarmService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName      name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param refreshSecs alarm severity should be reset every refreshSecs seconds to avoid being expired and set
   *                    to "Disconnected" (after three missed refreshes)
   * @param system      the Akka system or context, needed for working with futures and actors
   * @param timeout     amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  static IBlockingAlarmService getAlarmService(String asName, int refreshSecs, ActorRefFactory system, Timeout timeout) {
    return JBlockingAlarmService.lookup(asName, refreshSecs, system, timeout);
  }

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an IBlockingAlarmService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param system  the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  static IBlockingAlarmService getAlarmService(ActorRefFactory system, Timeout timeout) {
    return JBlockingAlarmService.lookup(defaultName, system, timeout);
  }

  /**
   * The default name that the Alarm Service is registered with
   */
  String defaultName = AlarmService$.MODULE$.defaultName();

  /**
   * An alarm's severity should be refreshed every defaultRefreshSecs seconds
   * to make sure it does not expire and become "Disconnected" (after maxMissedRefresh missed refreshes)
   */
  int defaultRefreshSecs = AlarmService$.MODULE$.defaultRefreshSecs();

  /**
   * The default number of refreshes that may be missed before an alarm's severity is expired
   * and becomes "Disconnected"
   */
  int maxMissedRefresh = AlarmService$.MODULE$.maxMissedRefresh();
}

