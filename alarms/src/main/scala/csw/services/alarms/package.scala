package csw.services

/**
 * == Alarm Service ==
 *
 * This project implements the CSW Alarm Service as described in
 * '''OSW TN019 - ALARM SERVICE PROTOTYPE DESIGN, TMT.SFT.TEC.16.004.REL01.DRF02'''.
 *
 * === Based on Redis ===
 *
 * There is no Alarm Service executable. The Alarm Service consists of an instance of Redis
 * that is registered with the [[csw.services.loc.LocationService `CSW Location Service`]]
 * using the [[csw.services.trackLocation.TrackLocation `trackLocation`]].
 * For example, the following command could be used to start a dedicated Redis instance for the Alarm Service on port 7777:
 *
 * {{{
 * tracklocation --name "Alarm Service" --command "redis-server --port %port" --port 7777
 * }}}
 *
 * The string "%port" is replaced with the actual port number. If you left off the --port option, a random, free port
 * would have been chosen.
 *
 * The client API for the Alarm Service takes a name (default: "Alarm Service") and uses it to lookup the Redis instance
 * with the Location Service.
 *
 * === How alarms are implemented and stored ===
 *
 * Alarms are stored in three places in Redis:
 *
 * - The static Alarm data, which is imported from a config file, is stored in a Redis Hash.
 *
 * - The Alarm's severity is stored in a separate Key and monitored for changes
 * using [[http://redis.io/topics/notifications Redis Keyspace Notifications]].
 *
 * - The current state of an alarm is stored in a separate Redis hash that includes the
 * latched and acknowledged state as well as the shelved and activation states of an alarm.
 *
 * === Alarm Keys ===
 *
 * The [[csw.services.alarms.AlarmKey `AlarmKey`]] class represents a key used to access
 * information about an alarm, set or get the severity, the current state or the static alarm information.
 * An alarm key is made up of the ``subsystem`` name, a ``component`` name and an alarm ``name``.
 *
 * === Internals ===
 *
 * The class provides three different keys to use to access the three locations mentioned above where the alarm data is stored:
 *
 * - `AlarmKey(subsystem, component, name).key` is used to access the static alarm data
 *
 * - `AlarmKey(subsystem, component, name).severityKey` is used to set/get the alarm's severity
 *
 * - `AlarmKey(subsystem, component, name).stateKey` is used internally to set/get the alarm's state
 *
 * The public API only deals with AlarmKey instances, so the above is only used internally.
 *
 * === Wildcards in Alarm Keys ===
 *
 * Some Alarm Service methods allow Alarm keys to contain wildcards. The AlarmKey class lets you
 * leave out any of the parameters, defaulting them to "*", which matches any subsystem, component or alarm name.
 * For example, to get a list all alarms in the TCS subsystem:
 *
 * {{{
 * alarmService.getAlarms(AlarmKey("TCS))
 * }}}
 *
 * Or to list all alarms in all subsystems:
 *
 * {{{
 * alarmService.getAlarms(AlarmKey())
 * }}}
 *
 * It is also possible to use the Redis wildcard syntax directly in the names passed to AlarmKey.
 *
 * Note that some commands require a unique key. For example, to set or get an alarm's severity,
 * you need a unique key with no wildcards.
 *
 * === Alarm Service Configuration File (ASCF) ===
 *
 * The alarm database is populated from a config file in HOCON format. This file should be generated from
 * information in the TMT ICD Database and contains descriptions of each alarm.
 * Here is an example with dummy data used for testing that shows the format of the file:
 *
 * {{{
 * alarms = [
 *   {
 *     subsystem = NFIRAOS
 *     component = envCtrl
 *     name = maxTemperature
 *     description = "Warns when temperature too high"
 *     location = "south side"
 *     alarmType = Absolute
 *     severityLevels = [Indeterminate, Okay, Warning, Major, Critical]
 *     probableCause = "too hot..."
 *     operatorResponse = "open window"
 *     acknowledge = true
 *     latched = true
 *   }
 *    {
 *     subsystem = NFIRAOS
 *     component = envCtrl
 *     name = minTemperature
 *     description = "Warns when temperature too low"
 *     location = "north side"
 *     alarmType = Absolute
 *     severityLevels = [Indeterminate, Okay, Warning, Major, Critical]
 *     probableCause = "too cold..."
 *     operatorResponse = "close window"
 *     acknowledge = true
 *     latched = true
 *   }
 *    {
 *     subsystem = TCS
 *     component = tcsPk
 *     name = cpuExceededAlarm
 *     description = "This alarm is activated when the tcsPk Assembly can no longer calculate all of its pointing values in the time allocated. The CPU may lock power, or there may be pointing loops running that are not needed. Response: Check to see if pointing loops are executing that are not needed or see about a more powerful CPU."
 *     location = "in computer..."
 *     alarmType = Absolute
 *     severityLevels = [Indeterminate, Okay, Warning, Major, Critical]
 *     probableCause = "too fast..."
 *     operatorResponse = "slow it down..."
 *     acknowledge = true
 *     latched = true
 *   }
 * ]
 * }}}
 *
 * === Static Alarm Model ===
 *
 * The [[csw.services.alarms.AlarmModel]] class represents the static alarm data,
 * as imported from the Alarm Service config file. This data is read-only.
 * The allowed values for `alarmType` and `severityLevels` are also defined in the [[csw.services.alarms.AlarmModel]] class.
 *
 * === Alarm State ===
 *
 * The [[csw.services.alarms.AlarmState]] class represents the runtime
 * internal state for an alarm. For example, this is where you can determine if an alarm is currently ``latched``
 * or ``activated``.
 *
 * === Command Line Application: asconsole ===
 *
 * The [[csw.services.asconsole.AsConsole asconsole application]] can be used from the command line to work with and test the Alarm Service.
 * It can be used to initialize the alarm data, set and get an alarm's severity, monitor alarms, etc.
 *
 *
 * === Alarm Scala API ===
 *
 * The Alarm Service Scala API is defined in the [[csw.services.alarms.AlarmService `AlarmService`]] trait.
 */
package object alarms {

}
