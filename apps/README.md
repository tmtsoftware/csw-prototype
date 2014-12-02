Applications
============

This directory contains a project for each application. Use "sbt stage" to install the applications under
the target directories.

* configServiceAnnex - an akka-http based file server for storing and retrieving large/binary files
  instead of checking them in to Git

* containerCmd - a command line application that takes a config file and starts a
  container with given  HCDs or assemblies.

* sequencer - A scala REPL shell for working with HCDs and assemblies
