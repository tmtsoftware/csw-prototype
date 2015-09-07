package csw.util.config

import java.util.UUID

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Events {

  /**
   * Base trait for all Configuration Types
   */
  sealed trait EventType {
    def eventId: String
    def timestamp: Long
    def source: String

    var data: ConfigData = ConfigData()

    def size = data.size

    def get(key: Key): Option[key.Value] = data.get(key)

    def set(key: Key)(value: key.Value): Unit = {
      data = data.set(key)(value)
    }

    def remove(key: Key): Unit = {
      data = data.remove(key)
    }

    // Shold do something about default value if no mapped value?
    def apply(key: Key): key.Value = data.get(key).get

    protected def doToString(kind: String) = kind + "[" + eventId + ", " + source + ", " + timestamp + "] " + data.toString

  }
  /**
   * Defines an observe event
   */
  case class ObserveEvent(eventId: String, timestamp: Long, source: String) extends EventType {
    override def toString = doToString("OE")
  }

  /**
   * A basic telemetry event
   * @param eventId a unique event id
   * @param timestamp the time the event was created
   * @param source the event source
   * @param prefix the prefix for the values (which only use simple names)
   */
  case class TelemetryEvent(eventId: String, timestamp: Long, source: String,
                            prefix: String) extends EventType {
    override def toString = doToString("TE")
  }

  object TelemetryEvent {
    val DEFAULT_PREFIX = ""

    def apply(source: String, prefix: String): TelemetryEvent = {
      val eventId = UUID.randomUUID().toString
      val timestamp = System.currentTimeMillis
      TelemetryEvent(eventId, timestamp, source, prefix)
    }

  }

  // XXX TODO: Add "endless" EventStream? (only neeeded if "pulling" events)

}