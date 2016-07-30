package csw.util.akka

import akka.actor.ActorRef
import csw.util.akka.PublisherActor._

/**
 * A client API for the PublisherActor
 *
 * @param publisherActorRef the PublisherActor's actorRef
 */
class PublisherActorClient(publisherActorRef: ActorRef) {
  /**
   * Subscribe the given actor to the publisher's state.
   * The subscriber will receive messages from the publisher whenever the publisher's state information changes.
   * (The type of message depends on the type parameter to the [[PublisherActor]].)
   */
  def subscribe(subscriber: ActorRef): Unit = publisherActorRef.tell(Subscribe, subscriber)

  /**
   * Unsubscribes the subscriber from the publisher
   */
  def unsubscribe(subscriber: ActorRef): Unit = publisherActorRef.tell(Unsubscribe, subscriber)

  /**
   * Causes a message to be sent to the subscribers with the current state.
   */
  def requestCurrent(): Unit = publisherActorRef ! RequestCurrent
}
