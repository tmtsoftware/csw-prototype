package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import csw.util.config.StateVariable.CurrentState

/**
  * This class distributes CurrentState events within the single ActorSystem of a component allowing "handlers"
  * to access a single stream of CurrentState events. The subscribe/unsubscriber messages are the same as the
  * ones used in HcdController
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
    case HcdController.Subscribe =>
      context.watch(sender())
      context.system.eventStream.subscribe(sender(), classOf[CurrentState])
    case HcdController.Unsubscribe =>
      unsubscribe(sender())
    case cs:CurrentState =>
      context.system.eventStream.publish(cs)
    case Terminated(actorRef) =>
      unsubscribe(actorRef)
    case x => log.error(s"CurrentStateReceiver received an unknown message: $x")
  }

  def unsubscribe(actorRef: ActorRef) = {
    context.unwatch(actorRef)
    context.system.eventStream.unsubscribe(actorRef)
  }

}

object CurrentStateReceiver {

  def props = Props[CurrentStateReceiver]()

  // Subscribes to CurrentState from a publisher
  case class AddPublisher(publisher: ActorRef)

  // Unsubscribes to CurrentState from a publisher
  case class RemovePublisher(publisher: ActorRef)
}

