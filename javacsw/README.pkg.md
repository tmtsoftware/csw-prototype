The pkg project deals with the packaging of components, such as Containers, HCDs and Assemblies.

See *OSW TN009 - "TMT CSW PACKAGING SOFTWARE DESIGN DOCUMENT"* for the background information.

Containers and their components can be created from a configuration file
(in <a href="https://github.com/typesafehub/config/blob/master/HOCON.md">HOCON</a> format)
by calling `ContainerComponent.create(com.typesafe.config.Config)` and
passing it the Config object for the file.

Alternatively, you can pass a `ContainerInfo` object describing
the container to the create() method. The [JComponent](src/main/java/javacsw/services/pkg/JComponent.java) class provides
a Java API for creating ContainerInfo objects for HCDs, assemblies and containers.

Components are controlled by a Supervisor actor ()[JSupervisor](src/main/java/javacsw/services/pkg/JSupervisor.java)) that
handles *lifecycle messages* to determine the state of the component
(Components that are not in the *Running* state, do not receive commands, for example).

Scala based components can extend the `LifecycleHandler` trait to be notified
about lifecycle changes, such as when the component is initialized, started and stopped.

Java based components should extend an abstract actor based class that includes that trait, such as
[JAssemblyControllerWithLifecycleHandler](src/main/java/javacsw/services/pkg/JAssemblyControllerWithLifecycleHandler.java) for assemblies or
[JHcdControllerWithLifecycleHandler](src/main/java/javacsw/services/pkg/JHcdControllerWithLifecycleHandler.java) for HCDs and then override
the lifecycle methods, such as startup(), initialize(), shutdown().

###Container Config Files

Here is an example of a config file for creating a container with the name <em>Container-1</em> that
contains one assembly (named <em>Assembly-1</em>) and depends on the services of two HCDs (<em>HCD-2A</em> and <em>HCD-2B</em>).
The assembly is implemented by the given class (TestAssembly).
A <em>Supervisor</em> actor will be created to manage the assembly, which includes registering it with the
location service, using the given name and prefix. The prefix can be used to distribute parts of the
configurations to different HCDs. HCDs register themselves with the Location Service and specify a unique
prefix that can be used for this purpose.
```
container {
  name = Container-1
  components {
    Assembly-1 {
      type = Assembly
      class = csw.services.pkg.TestAssembly
      prefix = tcs.base.assembly1
      connectionType: [akka]
      connections = [
        // Component connections used by this component
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
In this case, `HCD-2A` and `HCD-2B` are both implemented by the TestHcd.scala class.
```
container {
  name = "Container-2"
  components {
    HCD-2A {
      type = HCD
      class = csw.services.pkg.TestHcd
      prefix = tcs.base.pos
      connectionType: [akka]
      rate = 1 second
    }
    HCD-2B {
      type = HCD
      class = csw.services.pkg.TestHcd
      prefix = tcs.ao.pos.one
      connectionType: [akka]
      rate = 1 second
    }
  }
}
```
HCDs publish their status by calling the `notifySubscribers()` method.
Any actor that wishes to be notified about an HCDs status can subscribe to these messages.
An Assembly can subscribe to it's HCD's status by overriding the `allResolved()` method
(`allResolved()` is called once the locations of all the assembly's connections have been resolved):
```
 public void allResolved(Set<LocationService.Location> locations) {
    subscribe(locations, self());
 }
```
The HCDs can publish their current state by calling `notifySubscribers()` with a `CurrentState` object, or
by receiving a `CurrentState`  actor message
(from a worker actor), which is automatically handled by the parent trait.

For a working example, see the <a href="https://github.com/tmtsoftware/javacsw-pkg-demo">javacsw-pkg-demo</a>
project.
