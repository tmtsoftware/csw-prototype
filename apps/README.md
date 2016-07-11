Applications
============

This directory contains a project for each application. Use "sbt stage" to install the applications under
the target directories.

* [asConsole](asConsole) - a command line application for working with the [Alarm Service](../alarms)

* [configServiceAnnex](configServiceAnnex) - an akka-http based file server for storing and retrieving large/binary files
  instead of checking them in to Git or Svn

* [containerCmd](containerCmd) - a command line application that takes a config file and starts a
  container with given  HCDs or assemblies.

* [csClient](csClient) - a command line client to the config service

* [sequencer](sequencer) - A scala REPL shell for working with HCDs and assemblies

* [sysControl](sysControl) - A command line app for setting the log level of running components, sending lifecycle commands, etc.

* [trackLocation](trackLocation) - a command line app that wraps an external (non csw) application for the purpose of registering it
                   with the location service and unregistering it when it exits

