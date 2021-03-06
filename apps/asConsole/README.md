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
  --delete
        When used with --init, deletes the existing alarm data before importing
  --list
        Prints a list of all alarms (See other options to filter what is printed)
  --shutdown
        Shuts down the Alarm Service Redis instance
  --subsystem <subsystem>
        Limits the selected alarms to those belonging to the given subsystem
  --component <name>
        Limits the selected alarms to those belonging to the given component (subsystem should also be specified)
  --name <name>
        Limits the selected alarms to those whose name matches the given value (may contain Redis wildcards)
  --severity <severity>
        Sets the severity level for the alarm given by (--subsystem, --component, --name) to the given level (Alarm must be unique)
  --monitor-alarms <shell-command>
        Starts monitoring changes in the severity of alarm(s) given by (--subsystem, --component, --name) and calls the shell command with args: (subsystem, component, name, severity)
  --monitor-health <shell-command>
        Starts monitoring the health of the subsystems or components given by (--subsystem, --component, --name) and calls the shell command with one arg: Good, Ill or Bad
  --monitor-all
        With this option all severity changes are reported, even if shelved or out of service
  --acknowledge
        Acknowledge the alarm given by (--subsystem, --component, --name) (Alarm must be unique)
  --reset
        Reset the latched state of the alarm given by (--subsystem, --component, --name) (Alarm must be unique)
  --shelved <value>
        Set the shelved state of the alarm to true (shelved), or false (normal)
  --activated <value>
        Set the activated state of the alarm to true (activated), or false (normal)
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

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --monitor-alarms "echo ALARM: " --monitor-health "echo HEALTH: "

In this example we are monitoring changes in alarms in the `TCS` subsystem, component `tcsPk`, with the name `cpuExceededAlarm`.
If you leave off the --name option, all alarms in the component are monitored. If the --component option is missing,
all alarms in the subsystem are monitored. If --subsystem is missing, all alarms in all subsystems are monitored.

When one of the --monitor options is given, the asconsole command does not exit.
It continues to run and executes the given shell command whenever there are changes in the
alarm's severity or the health value.

In the above example, the "echo" shell command was passed to print out the alarm's severity or the health value.
You can pass any shell command here. The --monitor-alarms option is a script that takes five arguments: 
*subsystem component name latched-severity reported-severity*.
The --monitor-health option is a script that takes just one option: the health value: _Good, Ill or Bad_.

To change the severity of the alarm to Critical, use this command:

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --severity Critical --refresh --log DEBUG

The --refresh option will cause the command to keep running and refresh the alarm's severity every 5 seconds.
Type Ctrl-C to kill the command. Normally the severity would change to `Disconnected` after 15 seconds (3 * 5 secs),
however, since this is a *latched* alarm, it keeps the `Critical` severity until it is acknowledged.
To demonstrate this, enter this command to set the severity to `Okay` (It will remain `Critical` until acknowledged):

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --severity Okay --refresh --log DEBUG

In another window, acknowledge and reset the latched alarm:

    asconsole --subsystem TCS --component tcsPk --name cpuExceededAlarm --acknowledge --reset

Now the command monitoring the severity should start displaying `Okay` instead of `Critical`.
