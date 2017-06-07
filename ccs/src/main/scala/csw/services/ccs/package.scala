package csw.services

/**
 * == CCS - Command and Control Service ==
 *
 * The ccs project defines the basic classes and traits for the ''Command and Control Service''.
 *
 * Related projects are the ''util'' project, which defines the types for ''configurations'' and the
 * ''pkg'' project, which defines the Hcd and Assembly classes, lifecycle manager and supervisor for components.
 *
 * This is an actor based project.
 * The main actors (or actor traits) here are:
 *
 * - `AssemblyController` - trait for an actor that accepts configurations for an Assembly and communicates with
 * one or more HCDs or other assemblies before replying with a command status
 *
 * - `HcdController` - trait for an actor that accepts configurations (from an Assembly) for an HCD.
 * The HCD controller publishes its *current state*, and the assembly can subscribe to it to determine when a command has completed.
 *
 * - `HcdStatusMatcherActor` - an actor used to match demand and current state by subscribing to HCD status messages and then notifying a given actor
 *
 * The following classes are deprecated and may be removed in a future release:
 *
 * - `PeriodicHcdController` - like HcdController, but has a Queue of configurations that it checks at a specified rate.
 * This controller wakes up regularly, checks the incoming queue and updates its state variable with the current
 * state of the HCD.
 *
 * - `StateVariableMatcherActor` - an actor used to match demand and current state and then notify a given actor
 *
 * The `CommandStatus` class defines status messages to be returned from assemblies to indicate the completion status of a ''submit''.
 *
 * === Example HCD Controller Usage ===
 *
 * {{{
 *   class TestHcdController extends HcdController with Actor with ActorLogging {
 *
 *     // Use single worker actor to do work in the background
 *     // (could also use a worker per job/message if needed)
 *     val worker = context.actorOf(TestWorker.props())
 *
 *     // Send the config to the worker for processing
 *     override protected def process(config: Setup): Unit = {
 *       worker ! config
 *     }
 *
 *     // Optional override: Ask the worker actor to send us the current state (handled by parent trait)
 *     override protected def requestCurrent(): Unit = {
 *       worker ! TestWorker.RequestCurrentState
 *     }
 *
 *     // Use the default actor receive method defined in the parent
 *     override def receive: Receive = controllerReceive
 *   }
 * }}}
 *
 */
package object ccs {

}
