package javacsw.services.alarms;

import akka.actor.ActorRef;
import csw.services.alarms.*;
import scala.Unit;
import scala.concurrent.ExecutionContext;
import scala.runtime.BoxedUnit;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Java admin API for the alarm service.
 */
@SuppressWarnings({"SameParameterValue", "unused"})
public interface IAlarmServiceAdmin {
  /**
   * Initializes the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a future list of problems that occurred while validating the config file or ingesting the data into the database
   */
  CompletableFuture<List<AscfValidation.Problem>> initAlarms(File inputFile, boolean reset);

  /**
   * Gets the alarm information from the database for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a future sequence of alarm model objects
   */
  CompletableFuture<List<AlarmModel>> getAlarms(AlarmKey alarmKey);

  /**
   * Gets the alarm information from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm model object
   */
  CompletableFuture<AlarmModel> getAlarm(AlarmKey key);

  /**
   * Gets the alarm state from the database for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm state object
   */
  CompletableFuture<AlarmState> getAlarmState(AlarmKey key);

  /**
   * Gets the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @return a future severity level result
   */
  CompletableFuture<AlarmModel.CurrentSeverity> getSeverity(AlarmKey alarmKey);

  /**
   * Acknowledges the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  CompletableFuture<Unit> acknowledgeAlarm(AlarmKey alarmKey);

  /**
   * Resets the latched state of the given alarm, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  CompletableFuture<Unit> resetAlarm(AlarmKey alarmKey);

  /**
   * Acknowledges the given alarm and resets the latched state, if needed.
   *
   * @param alarmKey the key for the alarm
   * @return a future indicating when the operation has completed
   */
  CompletableFuture<Unit> acknowledgeAndResetAlarm(AlarmKey alarmKey);

  /**
   * Sets the shelved state of the alarm
   *
   * @param alarmKey     the key for the alarm
   * @param shelvedState the shelved state
   * @return a future indicating when the operation has completed
   */
  CompletableFuture<Unit> setShelvedState(AlarmKey alarmKey, AlarmState.ShelvedState shelvedState);

  /**
   * Sets the activation state of the alarm
   *
   * @param alarmKey        the key for the alarm
   * @param activationState the activation state
   * @return a future indicating when the operation has completed
   */
  CompletableFuture<Unit> setActivationState(AlarmKey alarmKey, AlarmState.ActivationState activationState);

  /**
   * Gets the health of the system, subsystem or component, based on the given alarm key.
   *
   * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @return the future health value (good, ill, bad)
   */
  CompletableFuture<AlarmModel.Health> getHealth(AlarmKey alarmKey);

  interface AlarmHandler {
    void handleAlarmStatus(AlarmModel.AlarmStatus status);
  }

  interface HealthHandler {
    void handleHealthStatus(AlarmModel.HealthStatus status);
  }

  /**
   * Starts monitoring the health of the system, subsystem or component
   *
   * @param alarmKey   an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
   *                   that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
   * @param subscriber an actor that will receive a HealthStatus message whenever the health for the given key changes
   * @param notifyAll  if true, all severity changes are reported (for example, for logging), otherwise
   *                   only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                   and where the latched severity or calculated health actually changed
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
    AlarmHandler notifyAlarm,
    HealthHandler notifyHealth,
    boolean notifyAll
  );

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   * @return a future indicating when shutdown has completed
   */
  CompletableFuture<Unit> shutdown();

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
   * @param ec required for futures
   * @return a future that completes when the redis server exits
   */
  static CompletableFuture<BoxedUnit> startAlarmService(String name, Boolean noExit, ExecutionContext ec) {
    return JAlarmServiceAdmin.startAlarmService(name, noExit, ec);
  }
}
