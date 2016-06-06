package javacsw.services.ccs

import akka.actor.{AbstractActor, Actor, ActorLogging}
import csw.services.ccs.{AssemblyController, HcdController}
import csw.util.config.StateVariable.CurrentState

/**
 * Supports Java subclasses of HcdController
 */
abstract class AbstractAssemblyController extends AbstractActor with ActorLogging with AssemblyController {
  /**
   * Notifies all subscribers with the given value (Need to override to keep java happy)
   */
  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)

  // XXX TODO: Override methods and make them java compat
}

