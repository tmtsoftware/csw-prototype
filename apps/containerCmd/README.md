ContainerCmd
============

This project adds support for building a command line application that takes a config file
and then starts a container with the given HCDs or assemblies.

See the [csw-pkg-demo](https://github.com/tmtsoftware/csw-pkg-demo) project for examples.

Location Service must be running
--------------------------------

Note that before starting a container, the location service (loc) must be running.
It can be started by typing: ../../loc/target/universal/stage/bin/loc.

ContainerCmd class
------------------

The ContainerCmd class can be used in a container project as follows:

```
import csw.services.apps.containerCmd.ContainerCmd

object MyContainer extends App {
  val a = args // Required to avoid null args below
  ContainerCmd(a, Some("myContainer.conf"))
}
```

Example Config File
-------------------

The format of the config file describing the container is the Typesafe config format, as
can be seen in the following examples.

This example creates a container containing a single assembly named Assembly-1.
An instance of the given Assembly1 class is created and the argument string "Assembly-1" is
passed to the constructor.
The assembly sends commands to four HCDs in another container
(The HCDs will be requested from the location service).


```
container {
  name = Container-1
  components {
    Assembly-1 {
      type = Assembly
      class = csw.pkgDemo.container1.Assembly1
      args = [Assembly-1]
      // uri = http://...
      services {
        // Services required by this component
        // Name: ServiceType
        HCD-2A: HCD
        HCD-2B: HCD
        HCD-2C: HCD
        HCD-2D: HCD
      }
    }
  }
}
```

In the following example, a container is created containing four HCDs.
In this case the same class is used for all the HCDs and the arguments (two strings)
are different for each. In reality you would normally use different HCD classes here.
The HCDs here don't require any services, but they will be registered with the location
service using the information provided here.


```
container {
    name = "Container-2"
    components {
        HCD-2A {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = tmt.mobie.blue.filter
            args = [HCD-2A, tmt.mobie.blue.filter]
        }
        HCD-2B {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = tmt.mobie.blue.disperser
            args = [HCD-2B, tmt.mobie.blue.disperser]
        }
        HCD-2C {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = tmt.tel.base.pos
            args = [HCD-2C, tmt.tel.base.pos]
        }
        HCD-2D {
            type = HCD
            class = csw.pkgDemo.container2.Hcd2
            path = tmt.tel.ao.pos.one
            args = [HCD-2D, tmt.tel.ao.pos.one]
        }
    }
}
```

Sbt Launcher Support (SbtContainerLauncher class)
-------------------------------------------------

The SbtContainerLauncher class can be used to start a container using the
[Sbt Launcher](http://www.scala-sbt.org/0.13.5/docs/Launcher/GettingStarted.html) API.
The idea is to be able to build containers without having to write any code.
The problem is that all required dependencies for the components that are started in the container
need to be in the classpath (and also available in the local ~/.ivy2 repository).

Unfortunately, the sbt launcher only includes the classpath of the one sbt project mentioned in the
launcher property file. So you need to either supply a special build.sbt with the necessary classpath
or specify another project that has the required classpath.

Here is an example property file for use with the sbt launcher:

```
[scala]
  version: 2.11.4
[app]
  org: org.tmt
  name: containerx
  version: 0.2-SNAPSHOT
  class: csw.services.apps.containerCmd.SbtContainerLauncher
  cross-versioned: binary
[repositories]
  local
[boot]
  directory: ${user.home}/.csw/boot
```

This defines a container using the classpath of the "containerx" project,
and the SbtContainerLauncher class as the main class. The jar files for the dependencies
are cached under ~/.csw/boot the first time the container app is run. (If you want to
update the jar files, you need to change the versions, or delete the cache.)

If you don't already have an sbt project available with the necessary dependencies
(for all the HCDs and assemblies listed in the config file), you can add a simple
build.sbt file, like the following:

```
val Version = "0.2-SNAPSHOT"

lazy val settings = Seq(
  organization := "org.tmt",
  version := Version,
  scalaVersion := "2.11.4"
)

val containerCmd = "org.tmt" %% "containercmd" % Version
val container2 = "org.tmt" %% "container2" % Version

lazy val root = (project in file(".")).
  settings(settings: _*).
  settings(
    name := "containerx",
    libraryDependencies ++= Seq(containerCmd, container2)
  )
```

along with a build.properties file:

```
sbt.version=0.13.7
```

Then run `sbt publishLocal` to install the (empty) project (required).

A script like the following can be used to start the container:

```
#!/bin/sh

name=containerX
launcher=/opt/local/share/sbt/sbt-launch.jar

java -Dsbt.boot.properties=$name.props -jar $launcher $name.conf
```

See the [csw-pkg-demo/containerX](https://github.com/tmtsoftware/csw-pkg-demo/containerX)
project for an example.

