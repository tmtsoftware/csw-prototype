package javacsw.services.kvs

import java.util.Optional
import java.util.concurrent.CompletableFuture

import akka.actor.ActorRefFactory
import csw.services.kvs.{KvsSettings, TelemetryService}
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
case class JTelemetryService(settings: KvsSettings, system: ActorRefFactory)
    extends ITelemetryService {

  private implicit val _system: ActorRefFactory = system
  import system.dispatcher

  private val ts = TelemetryService(settings)

  def set(status: StatusEvent): CompletableFuture[Unit] = ts.set(status).toJava.toCompletableFuture

  def set(status: StatusEvent, history: Int = 0): CompletableFuture[Unit] = ts.set(status, history).toJava.toCompletableFuture

  def get(prefix: String): CompletableFuture[Optional[StatusEvent]] = ts.get(prefix).map(_.asJava).toJava.toCompletableFuture

  /**
   * Gets a list of the n most recent status event values for the given prefix
   * @param prefix the status event's prefix
   * @param n the max number of values to get
   * @return future sequence of status events, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): CompletableFuture[java.util.List[StatusEvent]] = ts.getHistory(prefix, n).map(_.asJava).toJava.toCompletableFuture

  /**
   * Deletes the given status event from the store
   * @return a future indicating if/when the operation has completed
   */
  def delete(prefix: String): CompletableFuture[Unit] = ts.delete(prefix).map(_ ⇒ ()).toJava.toCompletableFuture

  /**
   * Disconnects from the key/value store server
   */
  def disconnect: CompletableFuture[Unit] = ts.disconnect().toJava.toCompletableFuture

  /**
   * Shuts the key/value store server down
   */
  def shutdown: CompletableFuture[Unit] = ts.shutdown().toJava.toCompletableFuture
}
