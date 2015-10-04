package csw.util.config

import java.util.UUID

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Events {

  /**
   * Base trait for all event types
   */
  sealed trait EventType extends KvsType {
    def eventId: String

    def timestamp: Long

    def source: String

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

  //  object ObserveEvent {
  //    import scala.pickling.Defaults._
  //    import scala.pickling.binary._
  //
  //    /**
  //     * Defines the automatic conversion to a ByteString and back again.
  //     */
  //    implicit val byteStringFormatter = new ByteStringFormatter[ObserveEvent] {
  //      def serialize(t: ObserveEvent): ByteString = {
  //        ByteString(t.pickle.value)
  //      }
  //
  //      def deserialize(bs: ByteString): ObserveEvent = {
  //        val ar = Array.ofDim[Byte](bs.length)
  //        bs.asByteBuffer.get(ar)
  //        ar.unpickle[ObserveEvent]
  //      }
  //    }
  //  }

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
    //    import scala.pickling.Defaults._
    //    import scala.pickling.binary._
    //
    //    /**
    //     * Defines the automatic conversion to a ByteString and back again.
    //     */
    //    implicit val byteStringFormatter = new ByteStringFormatter[TelemetryEvent] {
    //      def serialize(t: TelemetryEvent): ByteString = {
    //        ByteString(t.pickle.value)
    //      }
    //
    //      def deserialize(bs: ByteString): TelemetryEvent = {
    //        val ar = Array.ofDim[Byte](bs.length)
    //        bs.asByteBuffer.get(ar)
    //        ar.unpickle[TelemetryEvent]
    //      }
    //    }

    val DEFAULT_PREFIX = ""

    def apply(source: String, prefix: String): TelemetryEvent = {
      val eventId = UUID.randomUUID().toString
      val timestamp = System.currentTimeMillis
      TelemetryEvent(eventId, timestamp, source, prefix)
    }

  }

  // XXX TODO: Add "endless" EventStream? (only neeeded if "pulling" events)

}