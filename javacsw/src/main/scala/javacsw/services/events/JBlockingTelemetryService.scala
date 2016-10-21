package javacsw.services.events

import java.util.Optional

import akka.actor.ActorRefFactory
import csw.services.events._
import csw.util.config.Events.StatusEvent

import scala.concurrent.duration.FiniteDuration
import scala.compat.java8.OptionConverters._
import collection.JavaConverters._

/**
 * A wrapper API for a KVS that waits for operations to complete before returing.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 */
case class JBlockingTelemetryService(timeout: FiniteDuration, settings: EventServiceSettings, system: ActorRefFactory)
    extends IBlockingTelemetryService {

  private implicit val _system: ActorRefFactory = system
  private val ts = BlockingTelemetryService(TelemetryService(settings), timeout)(system)

  def publish(status: StatusEvent): Unit = ts.publish(status)

  def publish(status: StatusEvent, history: Int): Unit = ts.publish(status, history)

  def get(prefix: String): Optional[StatusEvent] = ts.get(prefix).asJava

  def getHistory(prefix: String, history: Int): java.util.List[StatusEvent] = ts.getHistory(prefix, history).asJava

  def delete(key: String): Unit = ts.delete(key)

  def disconnect(): Unit = ts.disconnect()

  def shutdown(): Unit = ts.shutdown()
}
