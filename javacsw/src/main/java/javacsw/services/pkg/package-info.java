/**
 * The pkg project deals with the packaging of components, such as Containers, HCDs and Assemblies.
 * <p>
 * See *OSW TN009 - "TMT CSW PACKAGING SOFTWARE DESIGN DOCUMENT"* for the background information.
 * <p>
 * Containers and their components can be created from a configuration file
 * (in <a href="https://github.com/typesafehub/config/blob/master/HOCON.md">HOCON</a> format)
 * by calling {@link csw.services.pkg.ContainerComponent#create(com.typesafe.config.Config)} and
 * passing it the Config object for the file.
 * <p>
 * Alternatively, you can pass a {@link csw.services.pkg.Component.ContainerInfo} object describing
 * the container to the create() method. The {@link javacsw.services.pkg.JComponent} class provides
 * a Java API for creating ContainerInfo objects for HCDs, assemblies and containers.
 * <p>
 * Components are controlled by a Supervisor ({@link csw.services.pkg.Supervisor},
 * {@link javacsw.services.pkg.JSupervisor}) actor that
 * handles <em>lifecycle messages</em> to determine the state of the component
 * (Components that are not in the *Running* state, do not receive commands, for example).
 * <p>
 * Scala based components can extend the {@link csw.services.pkg.LifecycleHandler} trait to be notified
 * about lifecycle changes, such as when the component is initialized, started and stopped.
 * Java based components should extend an abstract actor based class that includes that trait, such as
 * {@link javacsw.services.pkg.JAssemblyControllerWithLifecycleHandler} for assemblies or
 * {@link javacsw.services.pkg.JHcdControllerWithLifecycleHandler} for HCDs and then override
 * the lifecycle methods, such as startup(), initialize(), shutdown().
 * <p>
 * <strong>Container Config Files</strong>
 * <p>
 * Here is an example of a config file for creating a container with the name <em>Container-1</em> that
 * contains one assembly (named <em>Assembly-1</em>) and depends on the services of two HCDs (<em>HCD-2A</em> and <em>HCD-2B</em>).
 * The assembly is implemented by the given class (TestAssembly).
 * A <em>Supervisor</em> actor will be created to manage the assembly, which includes registering it with the
 * location service, using the given name and prefix. The prefix can be used to distribute parts of the
 * configurations to different HCDs. HCDs register themselves with the Location Service and specify a unique
 * prefix that can be used for this purpose.
 * <pre> {@code
 * container {
 *   name = Container-1
 *   components {
 *     Assembly-1 {
 *       type = Assembly
 *       class = csw.services.pkg.TestAssembly
 *       prefix = tcs.base.assembly1
 *       connectionType: [akka]
 *       connections = [
 *         // Component connections used by this component
 *         {
 *           name: HCD-2A
 *           type: HCD
 *           connectionType: [akka]
 *         }
 *         {
 *           name: HCD-2B
 *           type: HCD
 *           connectionType: [akka]
 *         }
 *       ]
 *     }
 *   }
 * }
 * } </pre>
 * Below is an example config file for the container with the HCDs referenced in the above example.
 * In this case, `HCD-2A` and `HCD-2B` are both implemented by the [TestHcd](src/multi-jvm/scala/csw/services/pkg/TestHcd.scala) class.
 * <pre> {@code
 * container {
 *   name = "Container-2"
 *   components {
 *     HCD-2A {
 *       type = HCD
 *       class = csw.services.pkg.TestHcd
 *       prefix = tcs.base.pos
 *       connectionType: [akka]
 *       rate = 1 second
 *     }
 *     HCD-2B {
 *       type = HCD
 *       class = csw.services.pkg.TestHcd
 *       prefix = tcs.ao.pos.one
 *       connectionType: [akka]
 *       rate = 1 second
 *     }
 *   }
 * }
 * } </pre>
 * HCDs publish their status by calling the <em>notifySubscribers()</em> method.
 * Any actor that wishes to be notified about an HCDs status can subscribe to these messages.
 * An Assembly can subscribe to it's HCD's status by overriding the <em>allResolved()</em> method
 * (<em>allResolved()</em> is called once the locations of all the assembly's connections have been resolved):
 * <pre> {@code
 public void allResolved(Set<LocationService.Location> locations) {
    subscribe(locations, self());
 }
 * } </pre>
 * The HCDs can publish their current state by calling <em>notifySubscribers()</em> with a
 * {@link csw.util.cfg.StateVariable.CurrentState} object, or
 * by receiving a {@link csw.util.cfg.StateVariable.CurrentState}  actor message
 * (from a worker actor), which is automatically handled by the parent trait.
 * <p>
 * For a working example, see the <a href="https://github.com/tmtsoftware/javacsw-pkg-demo">javacsw-pkg-demo</a>
 * project.
 */
package javacsw.services.pkg;