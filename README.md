TMT Common Software (CSW)
=========================

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org

Build Instructions
------------------

To build, run 'sbt' in the top level directory and type one of the following commands:

* compile - compiles the sources
* gen-idea - generates the Idea project and modules
* publish-local - copies the bundles to the local ivy repository (~/.ivy2) so they can be referenced by other bundles
* test - run the tests
* dist - create the Akka microkernel standalone apps (used in the ./test projects)
* multi-jvm:test - type: "project pkg" first and then "multi-jvm:test" to run this test

Commands apply to the entire build unless otherwise specified.
You can narrow the focus to a subproject with the sbt "project" command.
For example: "project cs" sets the current project to "cs". The top level project is "csw".

Projects
--------

* cmd - the Command Service (for sending commands to HCDs and Assemblies)
* cs - the Configuration Service (manages configuration files in Git repos)
* extjs - the ExtJS web UI (JavaScript with Sencha Cmd/ant build, not a Scala project)
* pkg - a packaging layer over the command service that provides HCD and Assembly classes
* test - for integration tests, demos

ExtJS Build
-----------

The extjs subdirectory contains the ExtJS sources for the web UIs. In addition,
the full ExtJS distribution needs to be in the extjs/ext directory, but has not be
checked in to Git, since it is generated and is quite large.
See <a href="extjs/Readme.md">extjs/Readme.md</a> for how to generate this directory.
