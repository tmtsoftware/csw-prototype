pkg - Component (Container, HCD, Assembly) Packaging
====================================================

This project deals with the packaging of components, such as Containers, HCDs and Assemblies.

Containers and their components can be created from a configuration file
(in [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format)
by calling [ContainerComponent.create(config)](src/main/scala/csw/services/pkg/ContainerComponent.scala) and
passing it the Config object for the file.

Components are controlled by a [Supervisor](src/main/scala/csw/services/pkg/Supervisor.scala) actor that
handles *lifecycle* messages to determine the state of the component
(Components that are not in the *Running* state, do not receive commands, for example).

Conponents can implement the [LifecycleHandler](src/main/scala/csw/services/pkg/LifecycleHandler.scala)
trait to be notified about lifecycle changes, such as when the component is initialized, started and stopped.

A demo/test can be found in the [multi-jvm](src/multi-jvm) directory and run with:
```sbt "project pkg" multi-jvm:test```

Container Config Files
----------------------

Here is an example of a config file for creating a container with the name *Container-1* that
contains one assembly (named *Assembly-1*) and depends on the services of two HCDs (*HCD-2A* and *HCD-2B*).
The assembly is implemented by the given class (`csw.pkgDemo.assembly1.Assembly1`).
A `Supervisor` actor will be created to manage the assembly, which includes registering it with the
location service, using the given name and prefix. The prefix can be used to distribute parts of the
configurations to different HCDs. HCDs register themselves with the Location Service and specify a unique
prefix that can be used for this purpose.

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

Below is an example config file for the container with the HCDs referenced in the above example.
In this case, `HCD-2A` and `HCD-2B` are both implemented by the `csw.pkgDemo.hcd2.Hcd2` class.

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

HCDs continually publish their status by calling the `notifySubscribers()` method.
Any actor that wishes to be notified about an HCDs status can subscribe to these messages.
An Assembly can subscribe to it's HCD's status like this (`allResolved()` is called once the
locations of all the assembly's connections have been resolved):

```
  /**
   * Called when all HCD locations are resolved.
   * Overridden here to subscribe to status values from the HCDs.
   */
  override protected def allResolved(locations: Set[Location]): Unit = {
    subscribe(locations)
  }

```

The HCDs can publish their current state by calling `notifySubscribers()` with a `CurrentState` object, or
by receiving a `CurrentState` actor message, which is automatically handled by the parent trait.

For examples, see the [TestAssembly](src/multi-jvm/scala/csw/services/pkg/TestAssembly.scala) in the multi-jvm test
and the [csw-pkg-demo](https://github.com/tmtsoftware/csw-pkg-demo) project or the Java based
[javacsw-pkg-demo](https://github.com/tmtsoftware/javacsw-pkg-demo) project.