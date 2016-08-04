package javacsw.services.events

import java.util.Optional
import java.util.concurrent.CompletableFuture
import javacsw.services.events.IEventService.EventHandler

import akka.actor.{ActorRef, ActorRefFactory}
import csw.services.events.EventService.EventMonitor
import csw.services.events.{EventService, EventServiceSettings}
import csw.util.config.Events.EventServiceEvent

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

/**
 * A Java wrapper API for a key/value store
 *
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 */
case class JEventService(settings: EventServiceSettings, system: ActorRefFactory)
    extends IEventService {

  private implicit val _system: ActorRefFactory = system
  import system.dispatcher

  private val eventService = EventService(settings)

  /**
   * Publishes the value for the given key
   *
   * @param event the value to store
   * @return a future indicating when the operation is done
   */
  override def publish(event: EventServiceEvent): CompletableFuture[Unit] = eventService.publish(event).toJava.toCompletableFuture

  /**
   * Publishes the value for the given key
   *
   * @param event the value to store
   * @param n the max number of history values to keep (0 means no history)
   * @return a future indicating when the operation is done
   */
  override def publish(event: EventServiceEvent, n: Int): CompletableFuture[Unit] =
    eventService.publish(event, n).toJava.toCompletableFuture


  override def subscribe(subscriber: Optional[ActorRef], callback: Optional[EventHandler], prefixes: String*): EventMonitor =
    eventService.subscribe(subscriber.asScala, callback.asScala.map(_.handleEvent), prefixes: _*)

  /**
   * Gets the event for the given prefix
   *
   * @param prefix the key
   * @return the future event, None if the prefix was not found
   */
  override def get(prefix: String): CompletableFuture[Optional[EventServiceEvent]] =
    eventService.get(prefix).map(_.asJava).toJava.toCompletableFuture

  /**
   * Returns a list containing up to the last n events for the given prefix
   *
   * @param prefix the event prefix to use
   * @param n future max number of history values to return
   */
  override def getHistory(prefix: String, n: Int): CompletableFuture[java.util.List[EventServiceEvent]] =
    eventService.getHistory(prefix, n).map(_.asJava).toJava.toCompletableFuture

  /**
   * Deletes the given key(s) from the store
   *
   * @return future number of keys that were deleted
   */
  override def delete(key: String): CompletableFuture[java.lang.Boolean] =
    eventService.delete(key).map(_ == 1L).map(Boolean.box).toJava.toCompletableFuture

  /**
   * Disconnects from the key/value store server
   */
  override def disconnect: CompletableFuture[Unit] =
    eventService.disconnect().toJava.toCompletableFuture

  /**
   * Shuts the key/value store server down
   */
  override def shutdown: CompletableFuture[Unit] =
    eventService.shutdown().toJava.toCompletableFuture
}
