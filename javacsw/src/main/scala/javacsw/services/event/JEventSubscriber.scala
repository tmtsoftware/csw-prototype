package javacsw.services.event

import akka.actor.AbstractActor
import csw.services.event.EventSubscriber

/**
 * Java API for Scala EventSubscriber trait
 */
abstract class JEventSubscriber extends AbstractActor with EventSubscriber {
  /**
   * Subscribes this actor to events with the given prefix.
   *
   * @param prefix the prefix for the events you want to subscribe to.
   */
  def subscribe(prefix: String): Unit = subscribe(List(prefix): _*)

  /**
   * Unsubscribes this actor from events from the given channel.
   *
   * @param prefix the top channel for the events you want to unsubscribe from.
   */
  def unsubscribe(prefix: String): Unit = unsubscribe(List(prefix): _*)
}
