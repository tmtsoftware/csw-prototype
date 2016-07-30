package javacsw.services.events

import java.util.Optional

import akka.actor.ActorRefFactory
import csw.services.events.EventService.EventFormatter
import csw.services.events.{BlockingEventService, EventServiceSettings, Implicits}
import csw.util.config.Configurations.{ControlConfigArg, SequenceConfigArg, _}
import csw.util.config.Events.{EventServiceEvent, ObserveEvent, StatusEvent, SystemEvent}
import csw.util.config.StateVariable._

import scala.concurrent.duration.Duration
import scala.compat.java8.OptionConverters._
import collection.JavaConverters._

object JBlockingEventService {
  import Implicits._
  // This is easier to do from Scala than from Java due to the use of implicits.

  def getStatusEventStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[StatusEvent] =
    JBlockingEventService[StatusEvent](timeout, settings, system)

  def getObserveEventStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[ObserveEvent] =
    JBlockingEventService[ObserveEvent](timeout, settings, system)

  def getSystemEventStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[SystemEvent] =
    JBlockingEventService[SystemEvent](timeout, settings, system)

  def getSetupConfigStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[SetupConfig] =
    JBlockingEventService[SetupConfig](timeout, settings, system)

  def getCurrentStateStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[CurrentState] =
    JBlockingEventService[CurrentState](timeout, settings, system)

  def getDemandStateStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[DemandState] =
    JBlockingEventService[DemandState](timeout, settings, system)

  def getStateVariableStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[StateVariable] =
    JBlockingEventService[StateVariable](timeout, settings, system)

  def getSetupConfigArgStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[SetupConfigArg] =
    JBlockingEventService[SetupConfigArg](timeout, settings, system)

  def getEventServiceEventStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[EventServiceEvent] =
    JBlockingEventService[EventServiceEvent](timeout, settings, system)

  def getSequenceConfigStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[SequenceConfig] =
    JBlockingEventService[SequenceConfig](timeout, settings, system)

  def getControlConfigStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[ControlConfig] =
    JBlockingEventService[ControlConfig](timeout, settings, system)

  def getSequenceConfigArgStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[SequenceConfigArg] =
    JBlockingEventService[SequenceConfigArg](timeout, settings, system)

  def getControlConfigArgStore(timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory): IBlockingEventService[ControlConfigArg] =
    JBlockingEventService[ControlConfigArg](timeout, settings, system)
}

/**
 * A wrapper API for a KVS that waits for operations to complete before returing.
 *
 * @param timeout the max amount of time to wait for an operation to complete
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 * @tparam T the type (or base type) of objects to store
 */
case class JBlockingEventService[T: EventFormatter](timeout: Duration, settings: EventServiceSettings, system: ActorRefFactory)
    extends IBlockingEventService[T] {

  private implicit val _system: ActorRefFactory = system
  private val kvs = BlockingEventService[T](timeout, settings)

  /**
   * Sets (and publishes) the value for the given key
   *
   * @param key the key
   * @param value the value to store
   */
  def set(key: String, value: T): Unit = kvs.set(key, value)

  /**
   * Sets (and publishes) the value for the given key
   *
   * @param key the key
   * @param value the value to store
   * @param n the max number of history values to keep (0 means no history)
   */
  def set(key: String, value: T, n: Int): Unit = kvs.set(key, value, n)

  /**
   * Gets the value of the given key
   *
   * @param key the key
   * @return the result, None if the key was not found
   */
  def get(key: String): Optional[T] = kvs.get(key).asJava

  /**
   * Returns a list containing up to the last n values for the given key
   *
   * @param key the key to use
   * @param n max number of history values to return
   */
  def getHistory(key: String, n: Int): java.util.List[T] = kvs.getHistory(key, n).asJava

  /**
   * Deletes the given key(s) from the store
   *
   * @return the number of keys that were deleted
   */
  def delete(key: String): Boolean = kvs.delete(key) == 1L

  /**
   * Sets a value for the given key, where the value itself is a map with keys and values.
   *
   * @param key the key
   * @param value the map of values to store
   * @return the result (true if successful)
   */
  def hmset(key: String, value: java.util.Map[String, String]): java.lang.Boolean = kvs.hmset(key, value.asScala.toMap)

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   *
   * @param key the key
   * @param field the key for a value in the map
   * @return the result string value for the field, if found
   */
  def hmget(key: String, field: String): Optional[String] = kvs.hmget(key, field).asJava

  /**
   * Disconnects from the key/value store server
   */
  def disconnect(): Unit = kvs.disconnect()

  /**
   * Shuts the key/value store server down
   */
  def shutdown(): Unit = kvs.shutdown()
}
