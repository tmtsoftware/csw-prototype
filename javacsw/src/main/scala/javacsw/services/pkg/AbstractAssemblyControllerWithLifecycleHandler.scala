package javacsw.services.pkg

import java.util.Optional

import akka.actor.{AbstractActor, ActorRef}
import csw.services.ccs.AssemblyController2
import csw.services.pkg.{Assembly, LifecycleHandler}
import csw.util.config.Configurations.{ObserveConfigArg, SetupConfigArg}
import csw.util.config.StateVariable.CurrentStates
import csw.services.ccs.Validation.Validation
import csw.services.pkg.Component.AssemblyInfo

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
abstract class AbstractAssemblyControllerWithLifecycleHandler(override val info: AssemblyInfo) extends AbstractActor
    with Assembly with AssemblyController2 with LifecycleHandler {

  /**
   * The default actor receive method for an assembly.
   * This method handles all the standard assembly and lifecycle messages.
   */
  def defaultReceive: Receive = controllerReceive orElse lifecycleHandlerReceive

  /**
   * A request to the implementing actor to publish the current state value
   * by calling notifySubscribers().
   */
  override def requestCurrent(): Unit = {}

  // Convert types for Java API
  override protected def setup(configArg: SetupConfigArg, replyTo: Option[ActorRef]): List[Validation] = setup(configArg, replyTo.asJava).asScala.toList

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param configArg contains a list of setup configurations
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  def setup(configArg: SetupConfigArg, replyTo: Optional[ActorRef]): java.util.List[Validation] = List.empty[Validation].asJava

  // Convert types for Java API
  override protected def observe(configArg: ObserveConfigArg, replyTo: Option[ActorRef]): List[Validation] = observe(configArg, replyTo.asJava).asScala.toList

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param configArg contains a list of observe configurations
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  def observe(configArg: ObserveConfigArg, replyTo: Optional[ActorRef]): java.util.List[Validation] = List.empty[Validation].asJava

  // -- These can be called from Java based subclasses

  override protected def notifySubscribers(a: CurrentStates): Unit = super.notifySubscribers(a)
}
