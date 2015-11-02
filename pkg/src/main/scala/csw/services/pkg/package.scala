package csw.services

/**
 * This project deals with the packaging of components, such as HCDs and Assemblies.
 *
 * Components are usually created by a [[csw.services.pkg.Container]] from a configuration file
 * and are then controlled by a [[csw.services.pkg.Supervisor]] actor that intercepts ''lifecycle'' messages
 * to determine the state of the component (Components that are not in the ''Running'' state, do not
 * receive commands, for example).
 *
 * Conponents can implement the [[csw.services.pkg.LifecycleHandler]] trait to be notified about
 * lifecycle changes, such as when the component is initialized, started and stopped.
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
 * location service, using the given name and prefix. The prefix can be used by a ''distributor'' type
 * actor that receives a large configuration and sends different parts of it to different assemblies,
 * based on the prefix.
 *
 * {{{
 * container {
 *   name = Container-1
 *   components {
 *     Assembly-1 {
 *       type = Assembly
 *       class = csw.services.pkg.TestAssembly
 *       prefix = tcs.base.assembly1
 *       args = [Assembly-1]
 *       services {
 *         // Services required by this component
 *         // Name: ServiceType
 *         HCD-2A: HCD
 *         HCD-2B: HCD
 *       }
 *     }
 *   }
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
 *       class = csw.services.pkg.TestHcd
 *       prefix = tcs.base.pos
 *       rate = 1 second
 *       args = [HCD-2A]
 *     }
 *     HCD-2B {
 *       type = HCD
 *       class = csw.services.pkg.TestHcd
 *       prefix = tcs.ao.pos.one
 *       rate = 1 second
 *       args = [HCD-2B]
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
