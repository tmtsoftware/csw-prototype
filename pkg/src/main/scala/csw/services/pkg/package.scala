package csw.services

/**
 * This project deals with the packaging of components, such as HCDs and Assemblies.
 *
 * Components live in a [[csw.services.pkg.Container]] and are controlled by a [[csw.services.pkg.Supervisor]].
 * Conponents can implement the [[csw.services.pkg.LifecycleHandler]] trait to be notified about
 * lifecycle changes, such as when the component is initialized, started and stopped.
 *
 * A demo/test can be found in the multi-jvm directory and run with:
 * {{{sbt "project pkg" multi-jvm:test}}}
 */
package object pkg {

}
