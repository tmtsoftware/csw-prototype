Java APIs for the Alarm Service
===============================

See the [alarms](../alarms) project for an overview.

Alarm Keys
----------

The [JAlarmKey](src/main/java/javacsw/services/alarms/JAlarmKey.java) class represents a key used to access
information about an alarm, set or get the severity, the current state or the static alarm information.
An alarm key is made up of the *subsystem* name, a *component* name and an alarm *name*.

Wildcards in Alarm Keys
-----------------------

Some Alarm Service methods allow Alarm keys to contain wildcards. The AlarmKey class lets you
leave out any of the parameters, defaulting them to "*", which matches any subsystem, component or alarm name.
For example, to get a list all alarms in the TCS subsystem:

```scala
alarmService.getAlarms(AlarmKey("TCS))
```

Or to list all alarms in all subsystems:

```scala
alarmService.getAlarms(AlarmKey())
```

It is also possible to use the Redis wildcard syntax directly in the names passed to AlarmKey.

Note that some commands require a unique key. For example, to set or get an alarm's severity,
you need a unique key with no wildcards.

Static Alarm Model
-------------------

The [JAlarmModel](src/main/java/javacsw/services/alarms/JAlarmModel.java) class represents the static alarm data,
as imported from the Alarm Service config file. This data is read-only.
The allowed values for *alarmType* and *severityLevels* are also defined in the JAlarmModel class.

Alarm State
-----------

The [JAlarmState](src/main/java/javacsw/services/alarms/JAlarmState.java) class represents the runtime
internal state for an alarm. For example, this is where you can determine if an alarm is currently *latched*
or *activated*.


Alarm Java API
---------------

The Alarm Service Java API is defined in the [IAlarmService](src/main/java/javacsw/services/alarms/IAlarmService.java) interface:

```java
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public interface IAlarmService {
    /**
     * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
     */
    int refreshSecs();

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
    CompletableFuture<SeverityLevel> getSeverity(AlarmKey alarmKey);

    /**
     * Acknowledges the given alarm, clearing the acknowledged and latched states, if needed.
     *
     * @param alarmKey the key for the alarm
     * @return a future indicating when the operation has completed
     */
    CompletableFuture<Unit> acknowledgeAlarm(AlarmKey alarmKey);

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
        return JAlarmService.lookup(AlarmService$.MODULE$.defaultName(), system, timeout);
    }

    /**
     * The default name that the Alarm Service is registered with
     */
    String defaultName;

    /**
     * An alarm's severity should be refreshed every defaultRefreshSecs seconds
     * to make sure it does not expire and become "Disconnected" (after maxMissedRefresh missed refreshes)
     */
    int defaultRefreshSecs;

    /**
     * The default number of refreshes that may be missed before an alarm's severity is expired
     * and becomes "Disconnected"
     */
    int maxMissedRefresh;
}
```