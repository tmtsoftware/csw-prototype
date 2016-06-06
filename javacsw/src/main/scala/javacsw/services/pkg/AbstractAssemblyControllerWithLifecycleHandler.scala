package javacsw.services.pkg

import java.util.Optional
import java.util.function.BiFunction

import collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.compat.java8.FunctionConverters._
import akka.actor.{AbstractActor, ActorLogging, ActorRef}
import akka.util.Timeout
import csw.services.ccs.AssemblyController
import csw.services.ccs.AssemblyController.Validation
import csw.services.loc.Connection
import csw.services.loc.LocationService.Location
import csw.services.pkg.{Assembly, LifecycleHandler}
import csw.util.config.Configurations.{ObserveConfigArg, SetupConfigArg}
import csw.util.config.RunId
import csw.util.config.StateVariable.{CurrentState, DemandState}

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
abstract class AbstractAssemblyControllerWithLifecycleHandler extends AbstractActor
    with ActorLogging with Assembly with AssemblyController with LifecycleHandler {

  /**
   * The default actor receive method for an assembly.
   * This method handles all the standard assembly and lifecycle messages.
   */
  def defaultReceive: Receive = controllerReceive orElse lifecycleHandlerReceive

  // -- These methods use Java types (Set, List, Optional, BiFunction) rather than the Scala counterparts --

  /**
   * Tracks the locations of the given connections
   */
  def trackConnections(connections: java.util.Set[Connection]): Unit =
    super.trackConnections(connections.asScala.toSet)

  override def trackConnections(connections: Set[Connection]): Unit =
    super.trackConnections(connections)

  /**
   * Stops tracking the locations of the given connections
   */
  def untrackConnections(connections: java.util.Set[Connection]): Unit =
    super.untrackConnections(connections.asScala.toSet)

  override def untrackConnections(connections: Set[Connection]): Unit =
    super.untrackConnections(connections)

  /**
   * Convenience method that can be used to monitor a set of state variables and reply to
   * the given actor when they all match the demand states, or reply with an error if
   * there is a timeout.
   *
   * @param demandStates list of state variables to be matched (wait until current state matches demand)
   * @param hcds         the target HCD actors
   * @param replyTo      actor to receive CommandStatus.Completed or CommandStatus.Error("timeout...") message
   * @param runId        runId to include in the command status message sent to the replyTo actor
   * @param timeout      amount of time to wait for states to match (default: 60 sec)
   * @param matcher      matcher to use (default: equality)
   */
  def matchDemandStates(demandStates: java.util.List[DemandState], hcds: java.util.Set[ActorRef],
                        replyTo: Optional[ActorRef], runId: RunId, timeout: Timeout,
                        matcher: BiFunction[DemandState, CurrentState, Boolean]): Unit =
    super.matchDemandStates(demandStates.asScala.toList, hcds.asScala.toSet, replyTo.asScala, runId, timeout, matcher.asScala)

  // -- Called from parent --

  /**
   * A request to the implementing actor to publish the current state value
   * by calling notifySubscribers().
   */
  override def requestCurrent(): Unit = {}

  override def setup(locationsResolved: Boolean, configArg: SetupConfigArg, replyTo: Option[ActorRef]): Validation =
    setup(locationsResolved, configArg, replyTo.asJava)

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param locationsResolved indicates if all the Assemblies connections are resolved
   * @param configArg         contains a list of setup configurations
   * @param replyTo           if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  def setup(locationsResolved: java.lang.Boolean, configArg: SetupConfigArg, replyTo: Optional[ActorRef]): Validation

  override def observe(locationsResolved: Boolean, configArg: ObserveConfigArg, replyTo: Option[ActorRef]): Validation =
    observe(locationsResolved, configArg, replyTo.asJava)

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param locationsResolved indicates if all the Assemblies connections are resolved
   * @param configArg         contains a list of observe configurations
   * @param replyTo           if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  def observe(locationsResolved: java.lang.Boolean, configArg: ObserveConfigArg, replyTo: Optional[ActorRef]): Validation

  override def allResolved(locations: Set[Location]): Unit = allResolved(new java.util.HashSet(locations.asJavaCollection))

  /**
   * Called when all locations are resolved
   * @param locations the resolved locations (of HCDs, etc.)
   */
  def allResolved(locations: java.util.Set[Location]): Unit

  // -- These can be called from Java based subclasses

  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)

  protected def distributeSetupConfigs(locationsResolved: Boolean, configArg: SetupConfigArg,
                                       replyTo: Optional[ActorRef]): Validation =
    super.distributeSetupConfigs(locationsResolved, configArg, replyTo.asScala)

  /**
   * Helper method to subscribe to status values (CurrentState objects) from the assembly's connections (to HCDs).
   * This can be called from the allResolved() method.
   *
   * @param locations  the resolved locations of the assembly's connections (to HCDs, for example)
   * @param subscriber the actor that should receive the status messages (CurrentState objects)
   */
  protected def subscribe(locations: java.util.Set[Location], subscriber: ActorRef): Unit =
    subscribe(locations.asScala.toSet, subscriber)

}
