package csw.util.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}

/**
 * Support for actors that can have subscribers
 */
object PublisherActor {

  sealed trait PublisherActorMessage

  /**
   * Subscribes the sender
   */
  case object Subscribe extends PublisherActorMessage

  /**
   * Unsubscribes the sender
   */
  case object Unsubscribe extends PublisherActorMessage

  // Message requesting the publisher to publish the current values
  case object RequestCurrent extends PublisherActorMessage

}

trait PublisherActor[A] {
  this: Actor with ActorLogging ⇒

  import PublisherActor._

  // List of actors that subscribe to this publisher
  private var subscribers = Set[ActorRef]()

  /**
   * This should be used by the implementer actor's receive method.
   * For example: def receive: Receive = publisherReceive orElse ...
   */
  def publisherReceive: Receive = {
    case Subscribe            ⇒ subscribe(sender())
    case Unsubscribe          ⇒ unsubscribe(sender())
    case RequestCurrent       ⇒ requestCurrent()
    case Terminated(actorRef) ⇒ unsubscribe(actorRef)
  }

  // Subscribes the given actorRef
  private def subscribe(actorRef: ActorRef): Unit = {
    if (!subscribers.contains(actorRef)) {
      subscribers += actorRef
      context.watch(actorRef)
      // Make sure the actor gets the current value right away
      requestCurrent()
    }
  }

  // Unsubscribes the given actorRef
  private def unsubscribe(actorRef: ActorRef): Unit = {
    subscribers -= actorRef
  }

  /**
   * Notifies all subscribers with the given value
   */
  protected def notifySubscribers(a: A): Unit = {
    subscribers.foreach(_ ! a)
  }

  /**
   * A request to the implementing actor to publish the current state value
   * by calling notifySubscribers().
   */
  protected def requestCurrent(): Unit
}
