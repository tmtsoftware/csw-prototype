package javacsw.services.events

import java.util.Optional
import javacsw.services.events.IEventService.EventHandler

import akka.actor.{ActorRef, ActorRefFactory}
import csw.services.events.EventService.EventMonitor
import csw.services.events._
import csw.util.config.Events.EventServiceEvent

import scala.concurrent.duration.Duration
import scala.compat.java8.OptionConverters._
import collection.JavaConverters._
import scala.annotation.varargs

/**
 * A wrapper API for a KVS that waits for operations to complete before returing.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 */
case class JBlockingEventService(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory)
    extends IBlockingEventService {

  private implicit val _system: ActorRefFactory = system
  private val eventService = BlockingEventService(timeout, settings)

  override def publish(event: EventServiceEvent): Unit = eventService.publish(event)

  override def publish(event: EventServiceEvent, n: Int): Unit = eventService.publish(event, n)

  override def subscribe(subscriber: Optional[ActorRef], callback: Optional[EventHandler], prefixes: String*): EventMonitor =
    eventService.subscribe(subscriber.asScala, callback.asScala.map(_.handleEvent), prefixes: _*)

  override def get(prefix: String): Optional[EventServiceEvent] = eventService.get(prefix).asJava

  override def getHistory(key: String, n: Int): java.util.List[EventServiceEvent] = eventService.getHistory(key, n).asJava

  override def delete(key: String): Boolean = eventService.delete(key) == 1L

  override def disconnect(): Unit = eventService.disconnect()

  override def shutdown(): Unit = eventService.shutdown()
}
