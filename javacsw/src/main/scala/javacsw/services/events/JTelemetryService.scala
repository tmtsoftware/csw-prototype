package javacsw.services.events

import java.util.Optional
import java.util.concurrent.CompletableFuture
import javacsw.services.events.IEventService.EventHandler

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.util.Timeout
import csw.services.events.EventService.EventMonitor
import csw.services.events.{EventServiceSettings, TelemetryService}
import csw.util.config.Events._

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

case object JTelemetryService {
  /**
   * Looks up the Redis instance for the Telemetry Service with the Location Service
   * and then returns an TelemetryService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name name used to register the Redis instance with the Location Service (default: "Telemetry Service")
   * @param sys required Akka environment
   * @param timeout amount of time to wait looking up name with the location service before giving up with an error
   * @return a future JTelemetryService instance
   */
  def lookup(name: String, sys: ActorSystem, timeout: Timeout): CompletableFuture[ITelemetryService] = {
    import sys.dispatcher
    TelemetryService(name)(sys, timeout).map(JTelemetryService(_, sys).asInstanceOf[ITelemetryService]).toJava.toCompletableFuture
  }
}

/**
 * A Java wrapper API for the telemetry service
 *
 * @param ts the underlying Scala TelemetryService implementation
 * @param system Akka env required for using actors, futures
 */
case class JTelemetryService(ts: TelemetryService, system: ActorRefFactory)
    extends ITelemetryService {

  private implicit val _system: ActorRefFactory = system
  import system.dispatcher

  /**
   * Returns a JTelemetryService instance using the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new JTelemetryService instance
   */
  def this(host: String, port: Int, sys: ActorSystem) {
    this(TelemetryService.get(host, port)(sys), sys)
  }

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
