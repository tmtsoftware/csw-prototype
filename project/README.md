CSW Sbt Build
=============

The top level sbt build is defined in the Csw class.
This is a multi-project build, so each subproject is defined in its own scala trait and referenced in the main or dependent builds.
Note that a build trait extends the traits of any other builds that it depends on.

* Csw - top level build
* Cs - top level build for the Configuration Service subproject
* Cmd - top level build for the Command Service subproject
* Settings - defines global settings used in all build files
* Test - defines an example standalone test application based on the Akka microkernel
* build.properties - defines the sbt version
