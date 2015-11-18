TMT Common Software (CSW)
=========================

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org

The latest API docs can always be found at http://tmtsoftware.github.io/csw/.

Version History
---------------

| Tag | API | Binary |
|-----|-----|
| CSW-0.2-PDR | [API Docs](https://cdn.rawgit.com/tmtsoftware/csw/CSW-API-0.2-PDR/index.html) | [Download](https://github.com/tmtsoftware/csw/releases/tag/v0.2-PDR) |


Build Instructions
------------------

To build, run 'sbt' in the top level directory and type one of the following commands:

* compile - compiles the sources
* test - run the tests (Note that not all tests run automatically, due to dependencies on external servers)
* multi-jvm:test - run tests that use multiple JVMs (Switch to project first for best results)
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

Install script
-----------

The script ./install.sh creates an install directory (../install) containing scripts and all of the required dependencies
for starting the CSW applications.


Projects and Directories
------------------------

* [apps](apps) - contains some standalone applications
* [ccs](ccs) - the Command and Control Service (for sending configurations to HCDs and Assemblies)
* [cs](cs) - the Configuration Service (manages configuration files in Git repos)
* [event](event) - the Event Service, based on HornetQ
* [kvs](kvs) - provides key/value store and publish/subscribe features based on Redis
* [loc](loc) - the Location Service (based on Multicast DNS, supports registering and finding Akka and http based services)
* [log](log) - contains logging related code
* [pkg](pkg) - a packaging layer over the command service that provides Container, Supervisor, HCD and Assembly classes
* [util](util) - for reusable utility code, including configuration and event classes

Applications
-----------

The following standalone applications are installed here:

* [cs](cs) - the config service

The following applications are defined under ../apps:

* [configServiceAnnex](configServiceAnnex) - an http server used to store and retrieve large files, used by the config server
* [containerCmd](containerCmd) - used to start containers of HCDs or assemblies, based on a given config file (This is not an application, but us used to simplify creating such applications)
* [sequencer](sequencer) - implements the command line sequencer application, which is a Scala REPL shell
* [csClient](csClient) - a command line client to the config service (used in some test scripts)


Publishing the API Documentation
--------------------------------

This project uses [GitHub Pages](https://pages.github.com/) for publishing the API documentation.
Sbt plugins are used to generate the scaladoc and publish it.
The most useful sbt tasks are *make-site* and *ghpages-push-site*, which generate the API docs and publish them on GitHub.

The main page for the site is `src/site-preprocess/index.html`. That page contains pointers to the 
API docs and the GitHub sources. Note that the links to the sources are always based on the branch
you are working in.


