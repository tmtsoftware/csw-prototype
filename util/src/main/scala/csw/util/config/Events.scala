package csw.util.config

import java.util.UUID

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Events {

  /**
   * Base trait for all event types
   */
  sealed trait EventType {
    def eventId: String
    def timestamp: Long
    def source: String

    /**
     * Holds the typed key/value pairs
     */
    def data: ConfigData

    /**
     * The number of key/value pairs
     */
    def size = data.size

    /**
     * Returns the value for the key, if found
     */
    def get(key: Key): Option[key.Value] = data.get(key)

    protected def doToString(kind: String) =
      kind + "[" + eventId + ", " + source + ", " + timestamp + "] " + data.toString

  }
  /**
   * Defines an observe event
   * @param eventId a unique event id
   * @param timestamp the time the event was created
   * @param source the event source
   * @param data the typed key/value pairs
   */
  case class ObserveEvent(eventId: String, timestamp: Long, source: String,
                          data: ConfigData = ConfigData()) extends EventType {

    def set(key: Key)(value: key.Value): ObserveEvent = ObserveEvent(eventId, timestamp, source, data.set(key)(value))

    def remove(key: Key): ObserveEvent = ObserveEvent(eventId, timestamp, source, data.remove(key))

    override def toString = doToString("OE")
  }

  /**
   * A basic telemetry event
   * @param eventId a unique event id
   * @param timestamp the time the event was created
   * @param source the event source
   * @param prefix the prefix for the values (which only use simple names)
   * @param data the typed key/value pairs
   */
  case class TelemetryEvent(eventId: String, timestamp: Long, source: String,
                            prefix: String, data: ConfigData = ConfigData()) extends EventType {

    def set(key: Key)(value: key.Value): TelemetryEvent = TelemetryEvent(eventId, timestamp, source, prefix, data.set(key)(value))

    def remove(key: Key): TelemetryEvent = TelemetryEvent(eventId, timestamp, source, prefix, data.remove(key))

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