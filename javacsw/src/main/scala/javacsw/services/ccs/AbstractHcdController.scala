package javacsw.services.ccs

import akka.actor.{AbstractActor, ActorLogging}
import csw.services.ccs.HcdController
import csw.util.config.StateVariable.CurrentState

/**
 * Supports Java subclasses of HcdController
 */
abstract class AbstractHcdController extends AbstractActor with ActorLogging with HcdController {
  /**
   * Notifies all subscribers with the given value (Need to override to keep java happy)
   */
  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)
}
