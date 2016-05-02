package javacsw.services.ccs

import akka.actor.{AbstractActor, Actor, ActorLogging}
import csw.services.ccs.HcdController
import csw.util.cfg.StateVariable.CurrentState

/**
  * Java API to HcdController
  */
abstract class JHcdController extends AbstractActor with ActorLogging with HcdController {
  /**
    * Notifies all subscribers with the given value (Need to override For Java API)
    */
  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)
}
