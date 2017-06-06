package javacsw.services.ccs

import akka.actor.AbstractActor
import csw.services.ccs.HcdController
import csw.util.param.StateVariable.CurrentState

/**
 * Supports Java subclasses of HcdController
 */
abstract class AbstractHcdController extends AbstractActor with HcdController {
  /**
   * Notifies all subscribers with the given value (Need to override to keep java happy)
   */
  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)
}
