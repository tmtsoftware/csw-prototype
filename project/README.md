CSW Sbt Build
=============

This is a multi-project build.

Build Instructions
------------------

To build, run 'sbt' in the top level directory and type one of the following commands:

* compile - compiles the sources
* test - run the tests
* stage - create the standalone apps and test apps (installed in */target/universal/stage/bin)
* publish-local - copies the bundles to the local ivy repository (~/.ivy2) so they can be referenced by other bundles
* doc - generates the scaladoc
* genjavadoc:doc - generates documentation in javadoc format
* unidoc - generates combined scaladocs for all projects