# TMT Common Software (CSW)

Common Software is the package of services and infrastructure software that integrates the TMT software systems.

http://www.tmt.org

Build Instructions
==================

There is no top level build yet.
To build the individual bundles, cd to the directory containing build.sbt and run sbt.
Some useful tasks:

osgi-bundle	- compiles and builds the osgi bundle
publish-local 	- copies the bundle to the local ivy repository (~/.ivy2) so it can be referenced by other bundles
test 		- run the tests
