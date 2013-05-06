# TMT Common Software (CSW)

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org


Build Instructions
==================

A hierarchical build is defined (see project/Csw.scala).
To build, run 'sbt' and type one of the following commands:

compile       - compiles the sources

gen-idea      - generates the Idea project and modules

osgi-bundle	  - compiles and builds the osgi bundles

publish-local - copies the bundles to the local ivy repository (~/.ivy2) so they can be referenced by other bundles

test 		  - run the tests

Commands apply to the entire build unless otherwise specified.
You can narrow the focus to a subproject with the sbt "project" command.
For example: "project cs" sets the current project to "cs". The top level project is "csw".
