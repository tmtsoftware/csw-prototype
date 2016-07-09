/**
 * Defines the Java APIs for the Alarm Service.
 * <p>
 * This project implements the CSW Alarm Service as described in
 * <em>OSW TN019 - ALARM SERVICE PROTOTYPE DESIGN, TMT.SFT.TEC.16.004.REL01.DRF02</em>.
 * <p>
 *  <strong>Based on Redis</strong>
 * <p>
 *  There is no Alarm Service executable. The Alarm Service consists of an instance of Redis
 *  that is registered with the CSW Location Service ({@link csw.services.loc.LocationService})
 *  using the <em>trackLocation application</em> ({@Link csw.services.trackLocation.TrackLocation}).
 *  For example, the following command could be used to start a dedicated Redis instance for the Alarm Service on port 7777:
 * <p>
 * <pre> {@code
 *  tracklocation --name "Alarm Service" --command "redis-server --port 7777" --port 7777
 * } </pre>
 * <p>
 *  The client API for the Alarm Service takes a name (default: "Alarm Service") and uses it to lookup the Redis instance
 *  with the Location Service.
 * <p>
 *  <strong>How alarms are implemented and stored</strong>
 * <p>
 *  Alarms are stored in three places in Redis:
 * <ul>
 *  <li> The static Alarm data, which is imported from a config file, is stored in a Redis Hash.
 *
 *  <li> The Alarm's severity is stored in a separate Key and monitored for changes
 *  using <a href="http://redis.io/topics/notifications">Redis Keyspace Notifications</a>.
 *
 *  <li> The current state of an alarm is stored in a separate Redis hash that includes the
 *  latched and acknowledged state as well as the shelved and activation states of an alarm.
 *  </ul>
 * <p>
 *  <strong>Alarm Keys</strong>
 * <p>
 *  The {@link javacsw.services.alarms.JAlarmKey} class represents a key used to access
 *  information about an alarm, set or get the severity, the current state or the static alarm information.
 *  An alarm key is made up of the *subsystem* name, a *component* name and an alarm *name*.
 * <p>
 *  <strong>Wildcards in Alarm Keys</strong>
 * <p>
 *  Some Alarm Service methods allow Alarm keys to contain wildcards. The AlarmKey class lets you
 *  leave out any of the parameters, defaulting them to "*", which matches any subsystem, component or alarm name.
 *  For example, to get a list all alarms in the TCS subsystem:
 *
 * <pre> {@code
 *  alarmService.getAlarms(AlarmKey("TCS))
 * } </pre>
 *
 *  Or to list all alarms in all subsystems:
 *
 * <pre> {@code
 *  alarmService.getAlarms(AlarmKey())
 * } </pre>
 * <p>
 *  It is also possible to use the Redis wildcard syntax directly in the names passed to AlarmKey.
 * <p>
 *  Note that some commands require a unique key. For example, to set or get an alarm's severity,
 *  you need a unique key with no wildcards.
 * <p>
 *  <strong>Static Alarm Model</strong>
 * <p>
 *  The {@link javacsw.services.alarms.JAlarmModel} class represents the static alarm data,
 *  as imported from the Alarm Service config file. This data is read-only.
 *  The allowed values for *alarmType* and *severityLevels* are also defined in the JAlarmModel class.
 * <p>
 *  <strong>Alarm State</strong>
 * <p>
 *  The {@link javacsw.services.alarms.JAlarmState} class represents the runtime
 *  internal state for an alarm. For example, this is where you can determine if an alarm is currently <em>latched</em>
 *  or <em>activated</em>.
 * <p>
 *  <strong>Alarm Java API</strong>
 * <p>
 *  The Alarm Service Java API is defined in the {@link javacsw.services.alarms.IAlarmService} interface.
 */
package javacsw.services.alarms;