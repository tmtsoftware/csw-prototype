ContainerCmd
============

This project adds support for building a command line application that takes a config file
and then starts a container with the given HCDs or assemblies.

See the [csw-pkg-demo](https://github.com/tmtsoftware/csw-pkg-demo) project for examples.

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

The config file describing the container is in [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format, as
can be seen in the following examples.

This example creates a container containing a single assembly named Assembly-1.
An instance of the given Assembly1 class is created and the argument string "Assembly-1" is
passed to the constructor.
The assembly sends commands to two HCDs in another container
(The HCDs will be requested from the location service).


```
container {
  name = Container-1
  components {
    Assembly-1 {
      type = Assembly
      class = csw.pkgDemo.assembly1.Assembly1
      prefix = tcs.base.assembly1
      connectionType: [akka]
      connections = [
        {
          name: HCD-2A
          type: HCD
          connectionType: [akka]
        }
        {
          name: HCD-2B
          type: HCD
          connectionType: [akka]
        }
      ]
    }
  }
}
```

In the following example, a container is created containing two HCDs.
In this case the same class is used for both HCDs. In reality you would normally use different HCD classes here.
The HCDs here don't require any services, but they will be registered with the location
service using the information provided here.

```
container {
  name = "Container-2"
  components {
    HCD-2A {
      type = HCD
      class = csw.pkgDemo.hcd2.Hcd2
      prefix = tcs.mobie.blue.filter
      connectionType: [akka]
      rate = 1 second
    }
    HCD-2B {
      type = HCD
      class = csw.pkgDemo.hcd2.Hcd2
      prefix = tcs.mobie.blue.disperser
      connectionType: [akka]
      rate = 1 second
    }
  }
}
```

