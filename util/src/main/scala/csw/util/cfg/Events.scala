package csw.util.cfg

import java.io.{ByteArrayInputStream, ObjectInputStream, ObjectOutputStream, ByteArrayOutputStream}
import java.util.{Date, UUID}

import csw.util.cfg.ConfigValues.ValueData
import csw.util.cfg.Configurations.CV

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Events {

  /**
   * Base trait for all Configuration Types
   */
  trait EventType {
    val eventId: String
    // = UUID.randomUUID().toString
    val timestamp: Long
    // = System.currentTimeMillis
    val source: String

    // Serializes this config (XXX TODO: Use Akka Protobuf serializer)
    def toBinary: Array[Byte] = {
      val bos = new ByteArrayOutputStream
      val out = new ObjectOutputStream(bos)
      out.writeObject(this)
      out.close()
      bos.toByteArray
    }
  }

  object EventType {
    // Deserializes a EventType object from bytes
    def apply(bytes: Array[Byte]): EventType = {
      val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
      val obj = in.readObject()
      in.close()
      obj.asInstanceOf[EventType]
    }
  }

  /**
   * A basic telemetry event
   * @param eventId a unique event id
   * @param timestamp the time the event was created
   * @param source the event source
   * @param prefix the prefix for the values (which only use simple names)
   * @param values collection of named values with optional units
   */
  case class TelemetryEvent(eventId: String, timestamp: Long, source: String,
                            prefix: String = TelemetryEvent.DEFAULT_PREFIX,
                            values: Set[CV] = Set.empty[CV]) extends EventType {

    lazy val size = values.size

    lazy val names: Set[String] = values.map(c => c.name)

    def :+[B <: CV](elem: B): TelemetryEvent = {
      TelemetryEvent(eventId, timestamp, source, prefix, values + elem)
    }

    def withValues(newValues: CV*): TelemetryEvent = {
      TelemetryEvent(eventId, timestamp, source, prefix, values ++ newValues)
    }

    override def toString = {
      val t = new Date(timestamp).toString
      t + ": [" + source + "]->" + prefix + " " + values
    }

    /**
     * Gets the ValueData associated with the given key
     * @param key the simple name for the value
     * @return Some[ValueData] if found, else None
     */
    def get(key: String): Option[ValueData[Any]] = {
      values.find(_.name == key).map(_.data)
    }

    /**
     * Gets the ValueData associated with the given key or throws an exception if not found
     * @param key the simple name for the value
     * @return the ValueData instance for the key
     */
    def apply(key: String): ValueData[Any] = get(key).get
  }

  object TelemetryEvent {
    val DEFAULT_PREFIX = ""

    def apply(eventId: String, timestamp: Long, source: String, prefix: String, values: CV*): TelemetryEvent = {
      TelemetryEvent(eventId, timestamp, source, prefix, values.toSet)
    }

    def apply(source: String, prefix: String, values: CV*): TelemetryEvent = {
      val eventId = UUID.randomUUID().toString
      val timestamp = System.currentTimeMillis
      TelemetryEvent(eventId, timestamp, source, prefix, values.toSet)
    }

    // Create from serialized bytes
    def apply(bytes: Array[Byte]): TelemetryEvent = {
      EventType(bytes).asInstanceOf[TelemetryEvent]
    }
  }

  /**
   * Defines an observe event (XXX TBD)
   */
  case class ObserveEvent(eventId: String, timestamp: Long, source: String) extends EventType

  object ObserveEvent {
    // Create from serialized bytes
    def apply(bytes: Array[Byte]): ObserveEvent = {
      EventType(bytes).asInstanceOf[ObserveEvent]
    }
  }

  // XXX TODO: Add "endless" EventStream? (only neeeded if "pulling" events)

}


