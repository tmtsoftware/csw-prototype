TMT Common Software (CSW)
=========================

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org

See [here](https://github.com/tmtsoftware/csw/releases/download/v0.3-PDR/TMTCommonSoftwareProgramming-CSWFD_REL01_KG.pdf)
for a detailed description of the CSW software.

The latest API docs can always be found at http://tmtsoftware.github.io/csw/.

For a list of recent changes, see the [CHANGELOG](CHANGELOG.md).

Version History
---------------

| Date | Tag | Docs | Source | Download |
|-----|-----|--------|-----|-----|
| 2016-12-03 | CSW-0.3-PDR | [API](http://tmtsoftware.github.io/csw/) | [csw-0.3](https://github.com/tmtsoftware/csw/tree/v0.3-PDR)| Source: [tar.gz](https://github.com/tmtsoftware/csw/archive/v0.3-PDR.tar.gz), [zip](https://github.com/tmtsoftware/csw/archive/v0.3-PDR.zip), Scala API Docs: [tar.gz](https://github.com/tmtsoftware/csw/releases/download/v0.3-PDR/csw-0.3-scaladoc.tar.gz), Java API Docs: [tar.gz](https://github.com/tmtsoftware/csw/releases/download/v0.3-PDR/csw-0.3-javadoc.tar.gz) |
| 2015-11-18 | CSW-0.2-PDR | [API](https://cdn.rawgit.com/tmtsoftware/csw/CSW-API-0.2-PDR/index.html) | [csw-0.2](https://github.com/tmtsoftware/csw/tree/v0.2-PDR)| Source: [tar.gz](https://github.com/tmtsoftware/csw/archive/v0.2-PDR.tar.gz), [zip](https://github.com/tmtsoftware/csw/archive/v0.2-PDR.zip), API Docs: [tar.gz](https://github.com/tmtsoftware/csw/archive/CSW-API-0.2-PDR.tar.gz), [zip](https://github.com/tmtsoftware/csw/archive/CSW-API-0.2-PDR.zip) |

Build Instructions
------------------

The easiest way to build this project is to run the `install.sh` script (or `quick-install.sh`, which skips
generating the documentation).

To build manually, run 'sbt' in the top level directory and type one of the following commands:

* compile - compiles the sources
* test - run the tests (Note that not all tests run automatically, due to dependencies on external servers)
* multi-jvm:test - run tests that use multiple JVMs (Switch to individual project first to avoid "address in use" errors or conflicts)
* stage - create the standalone apps and test apps (installed in */target/universal/stage/bin)
* publish-local - publish artifacts to the local ivy repository (~/.ivy2)
* unidoc - generates combined scaladocs for all projects

Java APIs
---------

Classes providing the CSW Java8 APIs can be found in the [javacsw](javacsw) subproject,
but also in the [util](util) subproject, where the Scala and Java APIs for configurations are found.

In many cases you can use the Scala classes directly from Java.
In cases where Scala specific features are used, Java API methods are provided or
alternative Java interfaces are provided. The Java interfaces use that same name as the Scala versions
but start with 'I', while Java API specific classes use thw same names, but start with 'J'.
See the API docs and Java test cases for how to use the CSW Java APIs.
  
Creating Installable Packages
-----------------------------

The following sbt commands generate packages that can be installed on various systems:

* universal:packageBin - Generates a universal zip file
* universal:packageZipTarball - Generates a universal tgz file
* debian:packageBin - Generates a deb
* rpm:packageBin - Generates an rpm
* universal:packageOsxDmg - Generates a DMG file with the same contents as the universal zip/tgz.

Install script
--------------

The script ./install.sh creates an install directory (../install) containing start scripts and all of the required dependencies
for starting the CSW applications, as well as the generated java and scala documentation.
The quick-install.sh script runs a bit faster, but does not generate the documentation.

Runtime Dependencies
--------------------

The Event, Telemetry, and Alarm services assumes that redis-server is running (http://redis.io/).

(The old event service (event_old) depends on an external Hornetq server running (http://hornetq.jboss.org/)).

Test Environment
----------------
Some of the test cases and demos depend on the Event, Telemetry or Alarm services assume that they are running and
registered with the Location Service. A script ([csw-services.sh](scripts/csw-services.sh)) is provided to start the 
services needed by the tests. Usage: csw-services.sh [start|stop].

Projects and Directories
------------------------

* [aas](aas) - Authentication and Authorization service (todo...)
* [alarms](alarms) - the Alarm Service (for setting and subscribing to alarms and system health)
* [apps](apps) - contains some standalone applications
* [ccs](ccs) - the Command and Control Service (for sending configurations to HCDs and Assemblies)
* [cs](cs) - the Configuration Service (manages configuration files in Git repos)
* [dbs](dbs) - Database Service (todo...)
* [event_old](event_old) - an earlier version of the Event Service, based on HornetQ
* [events](events) - provides Event Service, key/value store and publish/subscribe features based on Redis
* [examples](examples) - contains example projects, including Scala and Java versions of a "Vertical Slice" Assembly/HCD example
* [javacsw](javacsw) -  provides Java (8) APIs for the CSW classes
* [loc](loc) - the Location Service (based on Multicast DNS, supports registering and finding Akka and http based services)
* [log](log) - contains logging related code and configuration files
* [pkg](pkg) - a packaging layer over the command service that provides Container, Supervisor, HCD and Assembly classes
* [support](support) - intended to contain supporting code (todo...)
* [ts](ts) - implements the CSW Time Service based on Java 8 java.time and Akka
* [util](util) - for reusable utility code, including configuration and event classes

Applications
-----------

The following standalone applications are defined here:

* [cs](cs) - the config service

The following applications are defined under ../apps:

* [asConsole](apps/asConsole) - Alarm Service Console: a command line application for working with alarms
* [configServiceAnnex](apps/configServiceAnnex) - an http server used to store and retrieve large files, used by the config service
* [containerCmd](apps/containerCmd) - used to start containers of HCDs or assemblies, based on a given config file (This is not an application, but us used to simplify creating such applications)
* [csClient](apps/csClient) - a command line client to the config service (used in some test scripts)
* [sequencer](apps/sequencer) - implements the command line sequencer application, which is a Scala REPL shell
* [sysControl](apps/sysControl) - A command line app for setting the log level of running components, sending lifecycle commands, etc.
* [trackLocation](apps/trackLocation) - a command line app that wraps an external (non csw) application for the purpose of registering it with the location service and unregistering it when it exits

Examples
--------

The examples directory contains the following example applications:

* [assemblyExample](examples/assemblyExample) - a simple example assembly that sends messages to the example HCD
* [hcdExample](examples/hcdExample) - a simple example HCD that generates position events along with an event subscriber that logs them
* [vslice](examples/vslice) - a detailed, end to end, "Vertical Slice" example that demonstrates how to develop and test Assemblies and HCDs in Scala
* [vsliceJava](examples/vsliceJava) - a Java 8 version of the [vslice](examples/vslice) example that demonstrates how to use the CSW software from Java 8

Publishing the API Documentation
--------------------------------

This project uses [GitHub Pages](https://pages.github.com/) for publishing the API documentation.
Sbt plugins are used to generate the scaladoc and publish it.
The most useful sbt tasks are *make-site* and *ghpages-push-site*, which generate the API docs and publish them on GitHub.

The main page for the site is `src/site-preprocess/index.html`. That page contains pointers to the 
API docs and the GitHub sources. Note that the links to the sources are always based on the branch
you are working in.


