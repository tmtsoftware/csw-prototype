package javacsw.services.alarms;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.util.Timeout;
import csw.services.alarms.*;
import csw.services.alarms.AlarmModel.*;
import csw.services.alarms.AlarmService.*;
import csw.services.alarms.AlarmState.*;
import scala.Unit;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Defines the Java API to the Alarm Service
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "SameParameterValue"})
public interface IAlarmService {
    /**
     * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
     */
    int refreshSecs();

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
     * Sets and publishes the severity level for the given alarm
     *
     * @param alarmKey the key for the alarm
     * @param severity the new value of the severity
     * @return a future indicating when the operation has completed
     */
    CompletableFuture<Unit> setSeverity(AlarmKey alarmKey, SeverityLevel severity);

    /**
     * Gets the severity level for the given alarm
     *
     * @param alarmKey the key for the alarm
     * @return a future severity level result
     */
    CompletableFuture<CurrentSeverity> getSeverity(AlarmKey alarmKey);

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
    CompletableFuture<Unit> setShelvedState(AlarmKey alarmKey, ShelvedState shelvedState);

    /**
     * Sets the activation state of the alarm
     *
     * @param alarmKey        the key for the alarm
     * @param activationState the activation state
     * @return a future indicating when the operation has completed
     */
    CompletableFuture<Unit> setActivationState(AlarmKey alarmKey, ActivationState activationState);

    /**
     * Gets the health of the system, subsystem or component, based on the given alarm key.
     *
     * @param alarmKey an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
     *                 that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
     * @return the future health value (good, ill, bad)
     */
    CompletableFuture<Health> getHealth(AlarmKey alarmKey);

    interface AlarmHandler {
        void handleAlarmStatus(AlarmStatus status);
    }

    interface HealthHandler {
        void handleHealthStatus(HealthStatus status);
    }

    /**
     * Starts monitoring the health of the system, subsystem or component
     *
     * @param alarmKey     an AlarmKey matching the set of alarms for a component, subsystem or all subsystems, etc. (Note
     *                     that each of the AlarmKey fields may be specified as None, which is then converted to a wildcard "*")
     * @param subscriber   if defined, an actor that will receive a HealthStatus message whenever the health for the given key changes
     * @param notifyAlarm  if defined, a function that will be called with an AlarmStatus object whenever the severity of an alarm changes
     * @param notifyHealth if defined, a function that will be called with a HealthStatus object whenever the total health for key pattern changes
     * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
     *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
     *                     and where the latched severity or calculated health actually changed
     * @return an actorRef for the subscriber actor (kill the actor to stop monitoring)
     */
    AlarmMonitor monitorAlarms(
            AlarmKey alarmKey,
            Optional<ActorRef> subscriber,
            Optional<AlarmHandler> notifyAlarm,
            Optional<HealthHandler> notifyHealth,
            boolean notifyAll
    );

    // --- Static factory methods to create an IAlarmService instance --

    /**
     * Looks up the Redis instance for the Alarm Service with the Location Service
     * and then returns an IAlarmService instance using it.
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
    static CompletableFuture<IAlarmService> getAlarmService(String asName, int refreshSecs, ActorRefFactory system, Timeout timeout) {
        return JAlarmService.lookup(asName, refreshSecs, system, timeout);
    }

    /**
     * Looks up the Redis instance for the Alarm Service with the Location Service
     * and then returns an IAlarmService instance using it.
     * <p>
     * Note: Applications using the Location Service should call LocationService.initialize() once before
     * accessing any Akka or Location Service methods.
     *
     * @param system  the Akka system or context, needed for working with futures and actors
     * @param timeout amount of time to wait when looking up the alarm service with the location service
     * @return a new JAlarmService instance
     */
    static CompletableFuture<IAlarmService> getAlarmService(ActorRefFactory system, Timeout timeout) {
        return JAlarmService.lookup(defaultName, system, timeout);
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

