CSW System Remote Control Application
=====================================

The sysControl application allows you to send messages to CSW system actors to:

* change the log level for a given set of packages

* change the lifecycle state of a given component (startup, shutdown a component)

Usage
-----

```
Usage: sysControl [options]

  -n <name> | --name <name>
        Required: The name of the target component (as registered with the location service)
  --component-type <type>
        Required: component type of the target component: One of Container, HCD, Assembly, Service
  --connection-type <type>
        optional connection type to access the component: One of akka, http (default: akka)
  -p <name> | --package <name>
        Root package name for setting the log level (default: csw)
  --log-level <level>
        The new log level for the given package name: One of ALL, DEBUG, ERROR, TRACE, WARN, INFO, OFF
  --lifecycle <command>
        A lifecycle command to send to the target component: One of Shutdown, Startup, Remove, Initialize, Load, Uninitialize, Heartbeat
  --no-exit <value>
        for testing: prevents application from exiting after running command
  --help

  --version
```

Examples
--------

You can test the sysControl application by first running some scripts installed by
the [csw-pkg-demo](https://github.com/tmtsoftware/csw-pkg-demo) project. For example,
start a test assembly and two HCDs with this command:

    test_containers.sh

In another window, start the demo web app:

    demowebserver

Make some changes in the web app and you can see the log output of the HCDs and assembly in the first window.
Now, in another window, you can test the effects of these commands:

```
 ./syscontrol --name Assembly-1 --component-type Assembly --log-level ERROR
 ./syscontrol --name HCD-2B --component-type HCD --log-level ERROR
 ./syscontrol --name HCD-2A --component-type HCD --log-level ERROR
 ...
 ./syscontrol --name Assembly-1 --component-type Assembly --log-level DEBUG
 ./syscontrol --name HCD-2A --component-type HCD --log-level DEBUG
 ./syscontrol --name HCD-2B --component-type HCD --log-level DEBUG

 ./syscontrol --name HCD-2B --component-type HCD --lifecycle Shutdown
 ./syscontrol --name HCD-2B --component-type HCD --lifecycle Startup
```

Notice that when you shutdown an HCD, it no longer responds to commands.
