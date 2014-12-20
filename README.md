TMT Common Software (CSW)
=========================

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org

Build Instructions
------------------

To build, run 'sbt' in the top level directory and type one of the following commands:

* compile - compiles the sources
* test - run the tests
* multi-jvm:test - run tests that use multiple JVMs
* stage - create the standalone apps and test apps (installed in */target/universal/stage/bin)
* publish-local - publish artifacts to the local ivy repository (~/.ivy2)

Commands apply to the entire build unless otherwise specified.
You can narrow the focus to a subproject with the sbt "project" command.
For example: "project cs" sets the current project to "cs". The top level project is "csw".

Creating Installable Packages
-----------------------------

The following sbt commands generate packages that can be installed on various systems:

* universal:packageBin - Generates a universal zip file
* universal:packageZipTarball - Generates a universal tgz file
* debian:packageBin - Generates a deb
* rpm:packageBin - Generates an rpm
* universal:packageOsxDmg - Generates a DMG file with the same contents as the universal zip/tgz.
* windows:packageBin - Generates an MSI


Projects and Directories
------------------------

* apps - contains some standalone applications
* cmd - the Command Service (for sending commands to HCDs and Assemblies)
* cs - the Configuration Service (manages configuration files in Git repos)
* doc - for documentation
* event - the Event Service, based on HornetQ
* kvs - provides key/value store and publish/subscribe features based on Redis
* loc - the Location Service (a single actor that that supports registering and finding HCDs and assemblies)
* pkg - a packaging layer over the command service that provides HCD and Assembly classes
* util - for reusable utility code

