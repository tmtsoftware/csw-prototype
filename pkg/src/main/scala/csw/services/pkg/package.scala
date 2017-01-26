package csw.services

/**
 * This project deals with the packaging of components, such as HCDs and Assemblies.
 *
 * Components are usually created by a [[csw.services.pkg.ContainerComponent]] from a configuration file
 * and are then controlled by a [[csw.services.pkg.Supervisor]] actor that intercepts ''lifecycle'' messages
 * to determine the state of the component (Components that are not in the ''Running'' state, do not
 * receive commands, for example).
 *
 * A demo/test can be found in the multi-jvm directory and run with:
 * {{{sbt "project pkg" multi-jvm:test}}}
 *
 * === Container Config Files ===
 *
 * Here is an example of a config file for creating a container with the name ''Container-1'' that
 * contains one assembly (named ''Assembly-1'') and depends on the services of two HCDs (''HCD-2A'' and ''HCD-2B'').
 * The assembly is implemented by the given class (`csw.services.pkg.TestAssembly`).
 * A `Supervisor` actor will be created to manage the assembly, which includes registering it with the
 * location service, using the given name and prefix. The prefix can be used to distribute parts of the
 * configurations to different HCDs. HCDs register themselves with the Location Service and specify a unique
 * prefix that can be used for this purpose.
 *
 * {{{
 * container {
 * name = Container-1
 * components {
 *   Assembly-1 {
 *     type = Assembly
 *     class = csw.pkgDemo.assembly1.Assembly1
 *     prefix = tcs.base.assembly1
 *     connectionType: [akka]
 *     connections = [
 *       {
 *         name: HCD-2A
 *         type: HCD
 *         connectionType: [akka]
 *       }
 *       {
 *         name: HCD-2B
 *         type: HCD
 *         connectionType: [akka]
 *       }
 *     ]
 *   }
 *  }
 * }
 * }}}
 *
 * Below is an example config file for the container with the HCDs referenced in the above example.
 * In this case, `HCD-2A` and `HCD-2B` are both implemented by the `csw.services.pkg.TestHcd` class.
 * These are periodic HCDs that will have their `process` methods called at the given rate
 * (Each time process() is called, the HCDs check their input queues for configurations and update
 * their ''state variables'' with the current states).
 *
 * {{{
 * container {
 *   name = "Container-2"
 *   components {
 *     HCD-2A {
 *       type = HCD
 *       class = csw.pkgDemo.hcd2.Hcd2
 *       prefix = tcs.mobie.blue.filter
 *       connectionType: [akka]
 *       rate = 1 second
 *     }
 *     HCD-2B {
 *       type = HCD
 *       class = csw.pkgDemo.hcd2.Hcd2
 *       prefix = tcs.mobie.blue.disperser
 *       connectionType: [akka]
 *       rate = 1 second
 *     }
 *   }
 * }
 * }}}
 *
 * HCDs do not reply when they receive a configuration. The only way to know if an HCD has completed
 * its work is to check the value of its ''state variable'' in the key/value store.
 * For this you can use the ''StateMatcherActor'' from the ccs project.
 */
package object pkg {

}
