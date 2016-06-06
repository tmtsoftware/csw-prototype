package csw.util.config

import java.util.{Optional, UUID}
import scala.language.implicitConversions

import csw.util.config.UnitsOfMeasure.Units

import scala.annotation.varargs
import scala.compat.Platform

/**
 * Defines events used by the event and telemetry services
 */
object Events {
  import Configurations._

  case class EventTime(time: Long) {
    override def toString = time.toString
  }

  object EventTime {
    implicit def toEventTime(time: Long): EventTime = EventTime(time)

    implicit def toCurrent = EventTime(Platform.currentTime)
  }

  /**
   * This will include information related to the observation that is related to a configuration.
   * This will grow and develop.
   */
  case class EventInfo(source: ConfigKey, time: EventTime, obsId: Option[ObsId]) {

    /**
     * Unique ID for this event set
     */
    val eventId: UUID = UUID.randomUUID()

    override def toString = s"$source: eId: $eventId, time: $time, obsId: $obsId"
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
    self: T ⇒

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
    def eventId: UUID = info.eventId

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
  case class StatusEvent(info: EventInfo, items: ConfigData = Set.empty[Item[_, _]])
      extends EventType[StatusEvent] with EventServiceEvent {

    // Java API
    def this(prefix: String) = this(EventInfo(prefix))

    override def create(data: ConfigData) = StatusEvent(info, data)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): StatusEvent = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): StatusEvent = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): StatusEvent = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): StatusEvent = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): StatusEvent = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): StatusEvent = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): StatusEvent = super.remove[S, J](key)

    override def toString = doToString("StatusEvent")
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
  case class ObserveEvent(info: EventInfo, items: ConfigData = Set.empty[Item[_, _]])
      extends EventType[ObserveEvent] with EventServiceEvent {

    // Java API
    def this(prefix: String) = this(EventInfo(prefix))

    override def create(data: ConfigData) = ObserveEvent(info, data)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): ObserveEvent = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): ObserveEvent = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): ObserveEvent = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): ObserveEvent = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): ObserveEvent = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): ObserveEvent = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): ObserveEvent = super.remove[S, J](key)

    override def toString = doToString("ObserveEvent")
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
  case class SystemEvent(info: EventInfo, items: ConfigData = Set.empty[Item[_, _]])
      extends EventType[SystemEvent] with EventServiceEvent {

    override def create(data: ConfigData) = SystemEvent(info, data)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): SystemEvent = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): SystemEvent = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): SystemEvent = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): SystemEvent = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): SystemEvent = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): SystemEvent = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): SystemEvent = super.remove[S, J](key)

    override def toString = doToString("SystemEvent")
  }

  object SystemEvent {
    def apply(prefix: String, time: EventTime): SystemEvent = SystemEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): SystemEvent = SystemEvent(EventInfo(prefix, time, Some(obsId)))
  }
}
