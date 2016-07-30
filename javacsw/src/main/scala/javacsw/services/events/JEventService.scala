package javacsw.services.events

import java.util.Optional
import java.util.concurrent.CompletableFuture

import akka.actor.ActorRefFactory
import csw.services.events.EventService.EventFormatter
import csw.services.events.{EventService, EventServiceSettings, Implicits}
import csw.util.config.Configurations._
import csw.util.config.Events._
import csw.util.config.StateVariable._

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

object JEventService {
  import Implicits._
  // This is easier to do from Scala than from Java due to the use of implicits.

  def getStatusEventStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[StatusEvent] =
    JEventService[StatusEvent](settings, system)

  def getObserveEventStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[ObserveEvent] =
    JEventService[ObserveEvent](settings, system)

  def getSystemEventStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[SystemEvent] =
    JEventService[SystemEvent](settings, system)

  def getSetupConfigStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[SetupConfig] =
    JEventService[SetupConfig](settings, system)

  def getCurrentStateStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[CurrentState] =
    JEventService[CurrentState](settings, system)

  def getDemandStateStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[DemandState] =
    JEventService[DemandState](settings, system)

  def getStateVariableStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[StateVariable] =
    JEventService[StateVariable](settings, system)

  def getSetupConfigArgStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[SetupConfigArg] =
    JEventService[SetupConfigArg](settings, system)

  def getEventServiceEventStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[EventServiceEvent] =
    JEventService[EventServiceEvent](settings, system)

  def getSequenceConfigStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[SequenceConfig] =
    JEventService[SequenceConfig](settings, system)

  def getControlConfigStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[ControlConfig] =
    JEventService[ControlConfig](settings, system)

  def getSequenceConfigArgStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[SequenceConfigArg] =
    JEventService[SequenceConfigArg](settings, system)

  def getControlConfigArgStore(settings: EventServiceSettings, system: ActorRefFactory): IEventService[ControlConfigArg] =
    JEventService[ControlConfigArg](settings, system)

  // XXX TODO add other types
}

/**
 * A Java wrapper API for a key/value store
 *
 * @param settings Redis server settings
 * @param system Akka env required by RedisClient
 * @tparam T the type (or base type) of objects to store
 */
case class JEventService[T: EventFormatter](settings: EventServiceSettings, system: ActorRefFactory)
    extends IEventService[T] {

  private implicit val _system: ActorRefFactory = system
  import system.dispatcher

  private val kvs = EventService[T](settings)

  /**
   * Sets (and publishes) the value for the given key
   *
   * @param key the key
   * @param value the value to store
   * @return a future indicating when the operation is done
   */
  def set(key: String, value: T): CompletableFuture[Unit] = kvs.set(key, value).toJava.toCompletableFuture

  /**
   * Sets (and publishes) the value for the given key
   *
   * @param key the key
   * @param value the value to store
   * @param n the max number of history values to keep (0 means no history)
   * @return a future indicating when the operation is done
   */
  def set(key: String, value: T, n: Int): CompletableFuture[Unit] = kvs.set(key, value, n).toJava.toCompletableFuture

  /**
   * Gets the value of the given key
   *
   * @param key the key
   * @return the future result, None if the key was not found
   */
  def get(key: String): CompletableFuture[Optional[T]] = kvs.get(key).map(_.asJava).toJava.toCompletableFuture

  /**
   * Returns a list containing up to the last n values for the given key
   *
   * @param key the key to use
   * @param n future max number of history values to return
   */
  def getHistory(key: String, n: Int): CompletableFuture[java.util.List[T]] = kvs.getHistory(key, n).map(_.asJava).toJava.toCompletableFuture

  /**
   * Deletes the given key(s) from the store
   *
   * @return future number of keys that were deleted
   */
  def delete(key: String): CompletableFuture[java.lang.Boolean] = kvs.delete(key).map(_ == 1L).map(Boolean.box).toJava.toCompletableFuture

  /**
   * Sets a value for the given key, where the value itself is a map with keys and values.
   *
   * @param key the key
   * @param value the map of values to store
   * @return future result (true if successful)
   */
  def hmset(key: String, value: java.util.Map[String, String]): CompletableFuture[java.lang.Boolean] = kvs.hmset(key, value.asScala.toMap).map(Boolean.box).toJava.toCompletableFuture

  /**
   * This method is mainly useful for testing hmset. It gets the value of the given field
   * in the map that is the value for the given key. The value is returned here as a String.
   *
   * @param key the key
   * @param field the key for a value in the map
   * @return future result string value for the field, if found
   */
  def hmget(key: String, field: String): CompletableFuture[Optional[String]] = kvs.hmget(key, field).map(_.asJava).toJava.toCompletableFuture

  /**
   * Disconnects from the key/value store server
   */
  def disconnect: CompletableFuture[Unit] = kvs.disconnect().toJava.toCompletableFuture

  /**
   * Shuts the key/value store server down
   */
  def shutdown: CompletableFuture[Unit] = kvs.shutdown().toJava.toCompletableFuture
}
