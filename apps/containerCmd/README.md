ContainerCmd
============

This project adds support for building a command line application that takes a config file
and then starts a container with the given HCDs or assemblies.

See the [csw-pkg-demo](../../../csw-pkg-demo/) project for examples.

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
need to be in the classpath.

Unfortunately, the sbt launcher only includes the classpath of the one sbt project mentioned in the
launcher property file. So you need to either supply a special build.sbt with the necessary classpath
or specify another project that has the required classpath.

See the [csw-pkg-demo](../../../csw-pkg-demo/containerX/) project for an example.




