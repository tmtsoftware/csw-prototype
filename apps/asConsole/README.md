Command Line Application: asconsole
===================================

The asconsole application can be used from the command line to work with and test the Alarm Service.
Type `asconsole --help` to get a list of command line options:

```
asconsole 0.2-SNAPSHOT
Usage: asconsole [options]

  --as-name <name>
        The name that was used to register the Alarm Service Redis instance (Default: 'Alarm Service')
  --init <alarm-service-config-file>
        Initialize the set of available alarms from the given Alarm Service Config File (ASCF)
  --reset
        When used with --init, deletes the existing alarm data before importing
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

Note that some of the options (--monitor and --refresh) will cause the application to not exit. Use Control-C to
stop the application when needed.

Example Usage
-------------

The following commands demonstrate how to test the Alarm Service using the command line.
It is easiest to type each command in a separate terminal or tab. You can use the --log
option to control the amount of logging output to see what is going on.

Change the the csw install dir (relative to this dir):

    cd ../../../install/bin

Start the Redis instance and register it with the Command Service:

    tracklocation --name "Alarm Service" --command "redis-server --port 7777" --port 7777

Load the alarm information from the config file (Alarm Service Config File):

    asconsole --init ../../csw/apps/asConsole/src/test/resources/test-alarms.conf --log DEBUG

Once this command completes, you can start monitoring changes in an alarm's severity with this command:

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --monitor --log DEBUG

In this example we are monitoring changes in an alarm in the `TCS` subsystem, component `tcsPk`, with the name `cpuExceededAlarm`.
This command does not exit. It continues to run and prints out any changes in the alarm's severity.

To change the severity of the alarm to Critical, use this command:

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --severity Critical --refresh --log DEBUG

The --refresh option will cause the command to keep running and refresh the alarm's severity every 5 seconds.
Type Ctrl-C to kill the command. Normally the severity would change to `Indeterminate` after 15 seconds (3 * 5 secs),
however, since this is a *latched* alarm, it keeps the `Critical` severity until it is acknowledged.
To demonstrate this, enter this command to set the severity to `Okay` (It will remain `Critical` until acknowledged):

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --severity Okay --refresh --log DEBUG

In another window, acknowledge the latched alarm:

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --acknowledge

Now the command monitoring the severity should start displaying `Okay` instead of `Critical`.
