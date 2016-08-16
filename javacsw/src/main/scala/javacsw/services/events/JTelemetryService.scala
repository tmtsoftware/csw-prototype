package javacsw.services.events

import java.util.Optional
import java.util.concurrent.CompletableFuture

import akka.actor.ActorRefFactory
import csw.services.events.{EventServiceSettings, JAbstractSubscriber, TelemetryService}
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

  @Override
  def publish(status: StatusEvent): CompletableFuture[Unit] = ts.publish(status).toJava.toCompletableFuture

  @Override
  def publish(status: StatusEvent, history: Int = 0): CompletableFuture[Unit] = ts.publish(status, history).toJava.toCompletableFuture

  @Override
  def get(prefix: String): CompletableFuture[Optional[StatusEvent]] = ts.get(prefix).map(_.asJava).toJava.toCompletableFuture

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  @Override
  def getHistory(prefix: String, n: Int): CompletableFuture[java.util.List[StatusEvent]] = ts.getHistory(prefix, n).map(_.asJava).toJava.toCompletableFuture

  /**
   * Deletes the given status event from the store
   * @return a future indicating if/when the operation has completed
   */
  @Override
  def delete(prefix: String): CompletableFuture[Unit] = ts.delete(prefix).map(_ => ()).toJava.toCompletableFuture

  /**
   * Disconnects from the key/value store server
   */
  @Override
  def disconnect: CompletableFuture[Unit] = ts.disconnect().toJava.toCompletableFuture

  /**
   * Shuts the key/value store server down
   */
  @Override
  def shutdown: CompletableFuture[Unit] = ts.shutdown().toJava.toCompletableFuture
}
