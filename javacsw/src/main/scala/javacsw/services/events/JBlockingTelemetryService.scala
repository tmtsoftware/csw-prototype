package javacsw.services.events

import java.util.Optional

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.util.Timeout
import csw.services.events._
import csw.util.config.Events.StatusEvent

import scala.concurrent.duration.FiniteDuration
import scala.compat.java8.OptionConverters._
import collection.JavaConverters._
import scala.concurrent.Await

case object JBlockingTelemetryService {
  /**
   * Looks up the Redis instance for the Telemetry Service with the Location Service
   * and then returns a Java friendly JBlockingTelemetryService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param name name used to register the Redis instance with the Location Service (default: "Telemetry Service")
   * @param sys required Akka environment
   * @param timeout amount of time to wait looking up name with the location service before giving up with an error
   */
  def lookup(name: String, sys: ActorSystem, timeout: Timeout): IBlockingTelemetryService = {
    import sys.dispatcher
    val ts = Await.result(TelemetryService(name)(sys, timeout).map(BlockingTelemetryService(_, timeout.duration)), timeout.duration)
    JBlockingTelemetryService(ts, sys, timeout.duration)
  }
}

/**
 * A Java wrapper API for the telemetry service (blocking version)
 *
 * @param ts the underlying Scala BlockingTelemetryService implementation
 * @param system Akka env required for using actors, futures
 * @param timeout amount of time to wait for operations to complete before giving up with an error
 */
case class JBlockingTelemetryService(ts: BlockingTelemetryService, system: ActorRefFactory, timeout: FiniteDuration)
    extends IBlockingTelemetryService {

  // XXX TODO FIXME: Change to be method parameters
  private implicit val _system: ActorRefFactory = system
  import system._

  /**
   * Returns a JTelemetryService instance using the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new JTelemetryService instance
   */
  def this(host: String, port: Int, sys: ActorSystem, timeout: Timeout) {
    this(BlockingTelemetryService(TelemetryService.get(host, port)(sys), timeout.duration), sys, timeout.duration)
  }

  def publish(status: StatusEvent): Unit = ts.publish(status)

  def publish(status: StatusEvent, history: Int): Unit = ts.publish(status, history)

  def subscribe(subscriber: ActorRef, postLastEvents: Boolean, prefixes: String*): TelemetryService.TelemetryMonitor = ts.subscribe(subscriber, postLastEvents, prefixes: _*)

  def subscribe(callback: IBlockingTelemetryService.TelemetryHandler, postLastEvents: Boolean, prefixes: String*): TelemetryService.TelemetryMonitor = ts.subscribe(callback.handleEvent _, postLastEvents, prefixes: _*)

  def get(prefix: String): Optional[StatusEvent] = ts.get(prefix).asJava

  def getHistory(prefix: String, history: Int): java.util.List[StatusEvent] = ts.getHistory(prefix, history).asJava

  def delete(key: String): Unit = ts.delete(key)
}
