package javacsw.services.pkg

import java.util.Optional

import akka.actor.{AbstractActor, ActorRef}
import csw.services.ccs.AssemblyController
import csw.services.pkg.Assembly
import csw.util.itemSet.ItemSets.{Observe, Setup}
import csw.util.itemSet.StateVariable.CurrentStates
import csw.services.ccs.Validation.Validation
import csw.services.pkg.Component.AssemblyInfo
import csw.util.akka.PublisherActor

import scala.compat.java8.OptionConverters._

/**
 * Supports Java subclasses of AssemblyController
 */
abstract class AbstractAssemblyController(override val info: AssemblyInfo) extends AbstractActor
    with Assembly with AssemblyController {

  /**
   * The default actor receive method for an assembly.
   * This method handles all the standard assembly and lifecycle messages.
   */
  def defaultReceive: Receive = controllerReceive

  // Convert types for Java API
  override protected def setup(s: Setup, replyTo: Option[ActorRef]): Validation = setup(s, replyTo.asJava)

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param s setup configuration
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  def setup(s: Setup, replyTo: Optional[ActorRef]): Validation

  // Convert types for Java API
  override protected def observe(o: Observe, replyTo: Option[ActorRef]): Validation = observe(o, replyTo.asJava)

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param o observe configuration
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  def observe(o: Observe, replyTo: Optional[ActorRef]): Validation
}

/**
 * Supports Java subclasses of AssemblyController with PublisherActor[CurrentStates] mixed in
 */
abstract class AbstractAssemblyControllerWithPubSub(override val info: AssemblyInfo) extends AbstractAssemblyController(info)
    with PublisherActor[CurrentStates] {

  /**
   * The default actor receive method for an assembly.
   * This method handles all the standard assembly and lifecycle messages.
   */
  override def defaultReceive: Receive = publisherReceive orElse controllerReceive

  /**
   * Notifies all subscribers with the given value
   */
  override protected def notifySubscribers(a: CurrentStates): Unit = super.notifySubscribers(a)
}
