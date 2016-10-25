package javacsw.services.events

import java.util.concurrent.CompletableFuture
import javacsw.services.events.IEventService.EventHandler

import akka.actor.{ActorRef, ActorRefFactory}
import csw.services.events.EventService.EventMonitor
import csw.services.events.{EventService, EventServiceSettings}
import csw.util.config.Events.EventServiceEvent

import scala.compat.java8.FutureConverters._

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

  override def publish(event: EventServiceEvent): CompletableFuture[Unit] =
    eventService.publish(event).toJava.toCompletableFuture

  override def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*): EventMonitor =
    eventService.subscribe(subscriber, postLastEvents = true, prefixes: _*)

  override def subscribe(callback: EventHandler, postLastEvents: Boolean, prefixes: String*): EventMonitor =
    eventService.subscribe(callback.handleEvent _, postLastEvents = true, prefixes: _*)
}
