package javacsw.services.pkg

import akka.actor.{AbstractActor, ActorLogging}
import csw.services.ccs.HcdController
import csw.services.pkg.{Hcd, LifecycleHandler}
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.CurrentState

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
abstract class AbstractHcdControllerWithLifecycleHandler extends AbstractActor
    with ActorLogging with Hcd with HcdController with LifecycleHandler {

  /**
   * The default actor receive method
   */
  def defaultReceive = controllerReceive orElse lifecycleHandlerReceive

  // -- These methods use Java types (Set, List, Optional, BiFunction) rather than the Scala counterparts --

  // -- Called from parent --

  /**
   * A request to the implementing actor to publish the current state value
   * by calling notifySubscribers().
   */
  override def requestCurrent(): Unit = {}

  /**
   * A derived class should process the given config and either call
   * notifySubscribers() or send a CurrentState message to itself
   * (possibly from a worker actor) to indicate changes in the current HCD state.
   *
   * @param config            the config received
   */
  override def process(config: SetupConfig): Unit

  // -- These can be called from Java based subclasses

  /**
   * Notifies all subscribers with the given value
   */
  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)
}
