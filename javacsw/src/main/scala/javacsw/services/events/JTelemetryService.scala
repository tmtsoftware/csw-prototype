package javacsw.services.events

import java.util.Optional
import java.util.concurrent.CompletableFuture
import javacsw.services.events.IEventService.EventHandler

import akka.actor.{ActorRef, ActorRefFactory}
import csw.services.events.EventService.EventMonitor
import csw.services.events.{EventServiceSettings, TelemetryService}
import csw.util.config.Events._

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

/**
 * A Java wrapper API for the telemetry service
 *
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 */
case class JTelemetryService(settings: EventServiceSettings, system: ActorRefFactory)
    extends ITelemetryService {

  private implicit val _system: ActorRefFactory = system
  import system.dispatcher

  private val ts = TelemetryService(settings)

  override def publish(status: StatusEvent): CompletableFuture[Unit] = ts.publish(status).toJava.toCompletableFuture

  override def publish(status: StatusEvent, history: Int = 0): CompletableFuture[Unit] = ts.publish(status, history).toJava.toCompletableFuture

  override def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*): EventMonitor =
    ts.subscribe(subscriber, postLastEvents, prefixes: _*)

  override def subscribe(callback: ITelemetryService.TelemetryHandler, postLastEvents: Boolean, prefixes: String*): EventMonitor =
    ts.subscribe(callback.handleEvent _, postLastEvents, prefixes: _*)

  override def get(prefix: String): CompletableFuture[Optional[StatusEvent]] = ts.get(prefix).map(_.asJava).toJava.toCompletableFuture

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  override def getHistory(prefix: String, n: Int): CompletableFuture[java.util.List[StatusEvent]] = ts.getHistory(prefix, n).map(_.asJava).toJava.toCompletableFuture

  /**
   * Deletes the given status event from the store
   * @return a future indicating if/when the operation has completed
   */
  override def delete(prefix: String): CompletableFuture[Unit] = ts.delete(prefix).map(_ => ()).toJava.toCompletableFuture
}
