package javacsw.services.ccs

import akka.actor.{AbstractActor, ActorLogging}
import csw.services.ccs.AssemblyControllerOld
import csw.util.config.StateVariable.CurrentStates

/**
 * Supports Java subclasses of HcdController
 */
abstract class AbstractAssemblyControllerOld extends AbstractActor with ActorLogging with AssemblyControllerOld {
  /**
   * Notifies all subscribers with the given value (Need to override to keep java happy)
   */
  override protected def notifySubscribers(a: CurrentStates): Unit = super.notifySubscribers(a)

  // XXX TODO: Override methods and make them java compat
}

