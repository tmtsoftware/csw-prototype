package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ccs.HcdController
import csw.util.config.StateVariable.CurrentState

/**
  * This class distributes CurrentState events within the single ActorSystem of a component allowing "handlers"
  * to access a single stream of CurrentState events.
  *
  * Example:
  * <pre>
  *   import CurrentStateReceiver._
  *
  *   val stateReceiver = system.actorOf(CurrentStateReceiver.props)
  *
  *   // Add an HCD publisher
  *   stateReceiver ! AddPublisher(hcdActorRef)
  *
  *   // An actor wishing to receive CurrentState uses
  *   stateReceiver ! AddCurrentStateHandler(handlerActorRef)
  *
  *   // Later, it can stop receiving CurrentState using
  *   stateReceiver ! RemoveCurrentStateHandler(handlerActorRef)
  * </pre>
  *
  */
class CurrentStateReceiver()  extends Actor with ActorLogging {
  import CurrentStateReceiver._

  def receive:Receive = {
    case AddPublisher(publisher) =>
      publisher ! HcdController.Subscribe
    case RemovePublisher(publisher) =>
      publisher ! HcdController.Unsubscribe
    case AddCurrentStateHandler(handler: ActorRef) =>
      context.system.eventStream.subscribe(handler, classOf[CurrentState])
    case RemoveCurrentStateHandler(handler: ActorRef) =>
      context.system.eventStream.unsubscribe(handler)
    case cs:CurrentState =>
      context.system.eventStream.publish(cs)
    case x => log.error(s"StateReceive received an unknown message: $x")
  }

}

object CurrentStateReceiver {

  def props = Props[CurrentStateReceiver]()

  // Asks to receive CurrentState from the list of publishers
  case class AddPublisher(publisher: ActorRef)

  case class RemovePublisher(publisher: ActorRef)

  // Adds
  case class AddCurrentStateHandler(handler: ActorRef)

  case class RemoveCurrentStateHandler(handler: ActorRef)
}

