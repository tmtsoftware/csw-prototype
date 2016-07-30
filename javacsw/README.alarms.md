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

The Alarm Service Java API is defined in the [IAlarmService](src/main/java/javacsw/services/alarms/IAlarmService.java) interface.

