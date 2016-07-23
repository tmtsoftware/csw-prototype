package csw.services

/**
 * This package defines actors and actor traits for use by Assembly and HCD implementations.
 * The API to an actor consists of the message it receives (normally defined in the companion object)
 * and the replies it sends. These are documented in the individual actor classes.
 *
 * This is an [[http://akka.io/ Akka actor]] based project.
 * The main actors (or actor traits) here are:
 *
 * - '''[[csw.services.ccs.AssemblyController]]''' - trait for an actor that accepts configurations for an Assembly and communicates with
 * one or more HCDs or other assemblies before replying with a command status
 *
 * - '''[[HcdController]]''' - trait for an actor that accepts configurations (from an Assembly) for an HCD.
 * The HCD controller sets a state variable, which is used by the assembly to determine when a command has completed.
 *
 * - '''[[PeriodicHcdController]]''' - like HcdController, but has a Queue of configurations that it checks at a specified rate.
 * This controller wakes up regularly, checks the incoming queue and updates its state variable with the current
 * state of the HCD.
 *
 * - '''[[StateVariableMatcherActor]]''' - an actor used to match demand and current state variables (via Redis/kvs) and then notify a given actor
 *
 * - '''[[HcdStatusMatcherActor]]''' - an actor used to match demand and current state (by subscribing to HCD status messages) and then notify a given actor
 *
 * === Example ===
 *
 * Here is an example periodic controller. It delegates tasks to a worker actor.
 * The `additionalReceive` method could be used to receive custom actor messages.
 * The `process` method, called periodically, at a configured rate, is supposed
 * to process any incoming configs in its queue (which it gets with the `nextConfig` method),
 * and then update its state variables (done by the worker):
 *
 * {{{
 *   class TestPeriodicHcdController extends PeriodicHcdController {
 *
 *     // Use single worker actor to do work in the background
 *     // (could also use a worker per job/message if needed)
 *     val worker = context.actorOf(TestWorker.props())
 *
 *     override def additionalReceive: Receive = Actor.emptyBehavior
 *
 *     override protected def process(): Unit = {
 *       // Note: There could be some logic here to decide when to take the next config,
 *       // if there is more than one in the queue. (nextConfig is an Option, so this
 *       // only takes one config from the queue, if there is one there).
 *       nextConfig.foreach { config =>
 *         worker ! config
 *       }
 *     }
 *   }
 * }}}
 *
 * Here is the test worker actor. In this case it simulates doing some work
 * and then updates a state variable with the current settings.
 *
 * {{{
 *   class TestWorker extends Actor with ActorLogging {
 *
 *     import TestWorker._
 *     import context.dispatcher
 *
 *     val settings = KvsSettings(context.system)
 *     val svs = StateVariableStore(settings)
 *
 *     // Simulate getting the initial state from the device and publishing to the kvs
 *     val initialState = SetupConfig(testPrefix1).set(position, "None")
 *     svs.set(initialState)
 *
 *     def receive: Receive = {
 *       case config: SetupConfig =>
 *         // Update the demand state variable
 *         svs.setDemand(config)
 *         // Simulate doing work
 *         log.debug(s"Start processing \$config")
 *         context.system.scheduler.scheduleOnce(2.seconds, self, WorkDone(config))
 *
 *       case WorkDone(config) =>
 *         log.debug(s"Done processing \$config")
 *         // Simulate getting the current value from the device and publishing it to the kvs
 *         log.debug(s"Publishing \$config")
 *         svs.set(config)
 *
 *       case x => log.error(s"Unexpected message \$x")
 *     }
 *   }
 *
 * }}}
 *
 */
package object ccs {

}
