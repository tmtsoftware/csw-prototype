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
import csw.util.cfg.Configurations.{ObserveConfigArg, SetupConfigArg}
import csw.util.cfg.RunId
import csw.util.cfg.StateVariable.{CurrentState, DemandState}

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
abstract class AbstractAssemblyControllerWithLifecycleHandler extends AbstractActor
    with ActorLogging with Assembly with AssemblyController with LifecycleHandler {

  /**
   * The default actor receive method
   */
  def defaultReceive: Receive = controllerReceive orElse lifecycleHandlerReceive

  // -- These methods use Java types (Set, List, Optional, BiFunction) rather than the Scala counterparts --

  def trackConnections(connections: java.util.Set[Connection]): Unit =
    super.trackConnections(connections.asScala.toSet)

  override def trackConnections(connections: Set[Connection]): Unit =
    super.trackConnections(connections)

  def untrackConnections(connections: java.util.Set[Connection]): Unit =
    super.untrackConnections(connections.asScala.toSet)

  override def untrackConnections(connections: Set[Connection]): Unit =
    super.untrackConnections(connections)

  def matchDemandStates(demandStates: java.util.List[DemandState], hcds: java.util.Set[ActorRef],
                        replyTo: Optional[ActorRef], runId: RunId, timeout: Timeout,
                        matcher: BiFunction[DemandState, CurrentState, Boolean]): Unit =
    super.matchDemandStates(demandStates.asScala.toList, hcds.asScala.toSet, replyTo.asScala, runId, timeout, matcher.asScala)

  // -- Called from parent --

  override def requestCurrent(): Unit

  override def setup(locationsResolved: Boolean, configArg: SetupConfigArg, replyTo: Option[ActorRef]): Validation =
    setup(locationsResolved, configArg, replyTo.asJava)

  def setup(locationsResolved: java.lang.Boolean, configArg: SetupConfigArg, replyTo: Optional[ActorRef]): Validation

  override def observe(locationsResolved: Boolean, configArg: ObserveConfigArg, replyTo: Option[ActorRef]): Validation =
    observe(locationsResolved, configArg, replyTo.asJava)

  def observe(locationsResolved: java.lang.Boolean, configArg: ObserveConfigArg, replyTo: Optional[ActorRef]): Validation

  override def allResolved(locations: Set[Location]): Unit = allResolved(new java.util.HashSet(locations.asJavaCollection))

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
