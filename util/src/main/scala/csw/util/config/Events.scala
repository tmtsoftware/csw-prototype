package csw.util.config

import java.time.{Clock, Instant}
import java.util.UUID

import scala.language.implicitConversions

/**
 * Defines events used by the event and telemetry services
 */
object Events {
  import Configurations._

  case class EventTime(time: Instant = Instant.now(Clock.systemUTC)) {
    override def toString = time.toString
  }

  object EventTime {
    implicit def toEventTime(time: Instant): EventTime = EventTime(time)

    implicit def toCurrent = EventTime(Instant.now(Clock.systemUTC))
  }

  /**
   * This will include information related to the observation that is related to a configuration.
   * This will grow and develop.
   *
   * @param source the source subsystem and prefix for the component
   * @param time time of the event
   * @param obsId optional observation id
   * @param eventId automatically generated unique event id
   */
  case class EventInfo(source: ConfigKey, time: EventTime, obsId: Option[ObsId], eventId: String = UUID.randomUUID().toString) {
    override def toString = s"$source: eId: $eventId, time: $time, obsId: $obsId"

    override def equals(that: Any): Boolean = {
      that match {
        case that: EventInfo =>
          // Ignore the event ID && time to allow comparing events.  Is this right?
          this.source == that.source && this.obsId == that.obsId // && this.time == that.time
        case _               => false
      }
    }
  }

  object EventInfo {
    implicit def apply(prefix: String): EventInfo = {
      val configKey: ConfigKey = prefix
      EventInfo(configKey, EventTime.toCurrent, None)
    }

    implicit def apply(prefix: String, time: EventTime): EventInfo = {
      val configKey: ConfigKey = prefix
      EventInfo(configKey, time, None)
    }

    implicit def apply(prefix: String, time: EventTime, obsId: ObsId): EventInfo = {
      val configKey: ConfigKey = prefix
      EventInfo(configKey, time, Some(obsId))
    }

    // Java APIs
    def create(prefix: String): EventInfo = EventInfo(prefix)
  }

  /**
   * Base trait for event configurations
   *
   * @tparam T the subclass of ConfigType
   */
  sealed trait EventType[T <: EventType[T]] extends ConfigType[T] {
    self: T =>

    /**
     * Contains related event information
     */
    def info: EventInfo

    override def configKey: ConfigKey = info.source

    /**
     * The event source is the prefix
     */
    def source: String = configKey.prefix

    /**
     * The time the event was created
     */
    def rawTime: EventTime = info.time

    /**
     * The event id
     */
    def eventId: String = info.eventId

    /**
     * The observation ID
     */
    def obsIdOption[ObsId] = info.obsId
  }

  /**
   * Type of event used in the event service
   */
  sealed trait EventServiceEvent {
    /**
     * See prefix in ConfigType
     */
    def prefix: String
  }

  /**
   * Defines a status event
   *
   * @param info event related information
   * @param items an optional initial set of items (keys with values)
   */
  case class StatusEvent(info: EventInfo, items: ConfigData = Set.empty[Item[_]])
      extends EventType[StatusEvent] with EventServiceEvent {

    // Java API
    def this(prefix: String) = this(EventInfo(prefix))

    override def create(data: ConfigData) = StatusEvent(info, data)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): StatusEvent = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): StatusEvent = super.remove(key)
  }

  object StatusEvent {
    def apply(prefix: String, time: EventTime): StatusEvent = StatusEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): StatusEvent = StatusEvent(EventInfo(prefix, time, Some(obsId)))
  }

  /**
   * Defines a observe event
   *
   * @param info event related information
   * @param items an optional initial set of items (keys with values)
   */
  case class ObserveEvent(info: EventInfo, items: ConfigData = Set.empty[Item[_]])
      extends EventType[ObserveEvent] with EventServiceEvent {

    // Java API
    def this(prefix: String) = this(EventInfo(prefix))

    override def create(data: ConfigData) = ObserveEvent(info, data)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): ObserveEvent = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): ObserveEvent = super.remove(key)
  }

  object ObserveEvent {
    def apply(prefix: String, time: EventTime): ObserveEvent = ObserveEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): ObserveEvent = ObserveEvent(EventInfo(prefix, time, Some(obsId)))
  }

  /**
   * Defines a system event
   *
   * @param info event related information
   * @param items an optional initial set of items (keys with values)
   */
  case class SystemEvent(info: EventInfo, items: ConfigData = Set.empty[Item[_]])
      extends EventType[SystemEvent] with EventServiceEvent {

    // Java API
    def this(prefix: String) = this(EventInfo(prefix))

    override def create(data: ConfigData) = SystemEvent(info, data)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): SystemEvent = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): SystemEvent = super.remove(key)
  }

  object SystemEvent {
    def apply(prefix: String, time: EventTime): SystemEvent = SystemEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): SystemEvent = SystemEvent(EventInfo(prefix, time, Some(obsId)))
  }
}
