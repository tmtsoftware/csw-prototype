Alarm Service
=============

This project implements the CSW Alarm Service as described in
*OSW TN019 - ALARM SERVICE PROTOTYPE DESIGN, TMT.SFT.TEC.16.004.REL01.DRF02*.

Based on Redis
--------------

There is no Alarm Service executable. The Alarm Service consists of an instance of Redis
that is registered with the [CSW Location Service](../loc) using the [trackLocation application](../apps/trackLocation).
For example, the following command could be used to start a dedicated Redis instance for the Alarm Service on port 7777:

```
tracklocation --name "Alarm Service" --command "redis-server --port 7777" --port 7777
```

The client API for the Alarm Service takes a name (default: "Alarm Service") and uses it to lookup the Redis instance
with the Location Service.

How alarms are implemented and stored
-------------------------------------

Alarms are stored in three places in Redis:

* The static Alarm data, which is imported from a config file, is stored in a Redis Hash.

* The Alarm's severity is stored in a separate Key and monitored for changes
  using [Redis Keyspace Notifications](http://redis.io/topics/notifications).

* The current state of an alarm is stored in a separate Redis hash that includes the
  latched and acknowledged state as well as the shelved and activation states of an alarm.

Alarm Keys
----------

The [AlarmKey](src/main/scala/csw/services/alarms/AlarmKey.scala) class represents a key used to access
information about an alarm, set or get the severity, the current state or the static alarm information.
An alarm key is made up of the *subsystem* name, a *component* name and an alarm *name*.
The class provides three different keys to use to access the three locations mentioned above where the alarm data is stored:

* `AlarmKey(subsystem, component, name).key` is used to access the static alarm data

* `AlarmKey(subsystem, component, name).severityKey` is used to set/get the alarm's severity

* `AlarmKey(subsystem, component, name).stateKey` is used internally to set/get the alarm's state

The public API only deals with AlarmKey instances, the above is only used internally.

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

Alarm Service Configuration File (ASCF)
---------------------------------------

The alarm database is populated from a config file in HOCON format. This file should be generated from
information in the TMT ICD Database and contains descriptions of each alarm.
Here is an example with dummy data used for testing that shows the format of the file:

```hocon
alarms = [
  {
    subsystem = NFIRAOS
    component = envCtrl
    name = maxTemperature
    description = "Warns when temperature too high"
    location = "south side"
    alarmType = Absolute
    severityLevels = [Indeterminate, Okay, Warning, Major, Critical]
    probableCause = "too hot..."
    operatorResponse = "open window"
    acknowledge = true
    latched = true
  }
   {
    subsystem = NFIRAOS
    component = envCtrl
    name = minTemperature
    description = "Warns when temperature too low"
    location = "north side"
    alarmType = Absolute
    severityLevels = [Indeterminate, Okay, Warning, Major, Critical]
    probableCause = "too cold..."
    operatorResponse = "close window"
    acknowledge = true
    latched = true
  }
   {
    subsystem = TCS
    component = tcsPk
    name = cpuExceededAlarm
    description = "This alarm is activated when the tcsPk Assembly can no longer calculate all of its pointing values in the time allocated. The CPU may lock power, or there may be pointing loops running that are not needed. Response: Check to see if pointing loops are executing that are not needed or see about a more powerful CPU."
    location = "in computer..."
    alarmType = Absolute
    severityLevels = [Indeterminate, Okay, Warning, Major, Critical]
    probableCause = "too fast..."
    operatorResponse = "slow it down..."
    acknowledge = true
    latched = true
  }
]
```

Command Line Application: asconsole
-----------------------------------

The [asconsole application](../apps/asConsole) can be used from the command line to work with and test the Alarm Service.

```
asconsole 0.2-SNAPSHOT
Usage: asconsole [options]

  --as-name <name>
        The name that was used to register the Alarm Service Redis instance (Default: 'Alarm Service')
  --init <alarm-service-config-file>
        Initialize the set of available alarms from the given Alarm Service Config File (ASCF)
  --list
        Prints a list of all alarms (See other options to filter what is printed)
  --shutdown
        Shuts down the Alarm Service Redis instance
  --subsystem <subsystem>
        Limits the alarms returned by --list to the given subsystem
  --component <name>
        Limits the alarms returned by --list to the given component (subsystem must also be specified)
  --name <name>
        Limits the alarms returned by --list to those whose name matches the given value (may contain Redis wildcards)
  --severity <severity>
        Sets the severity level for the alarm given by (--subsystem, --component, --name) to the given level (Alarm must be unique)
  --monitor
        Starts monitoring changes in the severity of alarm(s) given by (--subsystem, --component, --name) (may contain Redis wildcards)
  --acknowledge
        Acknowledge the alarm given by (--subsystem, --component, --name) (Alarm must be unique)
  --refresh
        Continually refresh the given alarm's severity before it expires (use together with --subsystem, --component, --name, --severity)
  --refresh-secs <value>
        When --refresh was specified, the number of seconds between refreshes of the alarm's severity
  --no-exit
        For testing: prevents application from exiting the JVM
  --log <log-level>
        For testing: Sets the log level (default: OFF, choices: TRACE, DEBUG, INFO, WARN, ERROR, OFF)
  --help

  --version
```

Alarm Scala API
---------------

The Alarm Service Scala API is defined in the [AlarmService](src/main/scala/csw/services/alarms/AlarmService.scala) trait:

```scala
trait AlarmService {
  /**
   * Alarm severity should be reset every refreshSecs seconds to avoid being expired (after three missed refreshes)
   */
  def refreshSecs: Int

  /**
   * Initialize the alarm data in the Redis instance using the given file
   *
   * @param inputFile       the alarm service config file containing info about all the alarms
   * @return a future list of problems that occurred while validating the config file or ingesting the data into Redis
   */
  def initAlarms(inputFile: File): Future[List[Problem]]

  /**
   * Gets the alarm information from Redis for any matching alarms
   *
   * @param alarmKey a key that may match multiple alarms (via wildcards, see AlarmKey.apply())
   * @return a future sequence of alarm model objects
   */
  def getAlarms(alarmKey: AlarmKey): Future[Seq[AlarmModel]]

  /**
   * Gets the alarm information from Redis for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm model object
   */
  def getAlarm(key: AlarmKey): Future[AlarmModel]

  /**
   * Gets the alarm state from Redis for the matching Alarm
   *
   * @param key the key for the alarm
   * @return a future alarm state object
   */
  def getAlarmState(key: AlarmKey): Future[AlarmState]

  /**
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey        the key for the alarm
   * @param severity        the new value of the severity
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
   * @param alarmKey        the key for the alarm
   * @return a future indicating when the operation has completed
   */
  def acknowledgeAlarm(alarmKey: AlarmKey): Future[Unit]

  /**
   * Starts monitoring the severity levels of the alarm(s) matching the given key
   *
   * @param alarmKey      the key for the alarm
   * @param subscriberOpt if defined, an actor that will receive an AlarmStatus message whenever the severity of an alarm changes
   * @param notifyOpt     if defined, a function that will be called with an AlarmStatus object whenever the severity of an alarm changes
   * @return an actorRef for the subscriber actor (kill the actor to stop monitoring)
   */
  def monitorAlarms(alarmKey: AlarmKey, subscriberOpt: Option[ActorRef] = None, notifyOpt: Option[AlarmStatus ⇒ Unit] = None): AlarmMonitor

  /**
   * Shuts down the Redis server (For use in test cases that started Redis themselves)
   */
  def shutdown(): Unit
}

```