package csw.util.cfg

import java.util.{Date, UUID}

import csw.util.cfg.ConfigValues.ValueData
import csw.util.cfg.Configurations.CV
import csw.util.cfg.UnitsOfMeasure.Units

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Events {

  /**
   * Base trait for all Configuration Types
   */
  trait EventType {
    val eventId: String
    val timestamp: Long
    val source: String

    /**
     * Serializes this config to a byte array using google protobufs
     */
    def toBinary: Array[Byte]
  }

  object EventType {
    /**
     * Deserializes an EventType object from protobuf bytes
     */
    def apply(bytes: Array[Byte]): EventType = {
      val et = Protobuf.EventType.parseFrom(bytes)
      if (et.hasObserveEvent) {
        ObserveEvent(et.getObserveEvent)
      } else if (et.hasTelemetryEvent) {
        TelemetryEvent(et.getTelemetryEvent)
      } else throw new RuntimeException("No event found in protobuf")
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

    def :+[B <: CV](value: B): TelemetryEvent = {
      TelemetryEvent(eventId, timestamp, source, prefix, values + value)
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

    /**
     * Returns true if the event contains the given key
     */
    def exists(key: String): Boolean = values.exists(_.name == key)

    override def toBinary: Array[Byte] = {
      import scala.collection.JavaConversions._

      def getElems[A](elems: Seq[A]): Seq[Protobuf.A] = {
        val b = Protobuf.A.newBuilder()
        for (e <- elems) yield
          e match {
            case s: String => b.setStringVal(s).build()
            case i: Int => b.setIntVal(i).build()
            case l: Long => b.setLongVal(l).build()
            case d: Double => b.setDoubleVal(d).build()
          }
      }

      def getValueData(cv: CV): Protobuf.ValueData = {
        Protobuf.ValueData.newBuilder()
          .setUnits(cv.units.name)
          .addAllElems(getElems(cv.elems))
          .build()
      }

      def getValues = for (cv <- values) yield
        Protobuf.CV.newBuilder()
          .setTrialName(cv.trialName)
          .setData(getValueData(cv))
          .build()

      Protobuf.EventType.newBuilder().setTelemetryEvent(
        Protobuf.TelemetryEvent.newBuilder()
          .setEventId(eventId)
          .setTimestamp(timestamp)
          .setSource(source)
          .setPrefix(prefix)
          .addAllValues(getValues)
      ).build().toByteArray
    }
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

    def apply(oe: Protobuf.TelemetryEvent): TelemetryEvent = {
      import scala.collection.JavaConversions._

      def getElems(l: List[Protobuf.A]): Seq[Any] = {
        for (a <- l) yield
          if (a.hasStringVal) a.getStringVal
          else if (a.hasIntVal) a.getIntVal
          else if (a.hasLongVal) a.getLongVal
          else if (a.hasDoubleVal) a.getDoubleVal
      }

      def getValueData(data: Protobuf.ValueData): ValueData[Any] = {
        ValueData(getElems(data.getElemsList.toList), Units.fromString(data.getUnits))
      }

      def getValues(seq: Seq[Protobuf.CV]): Set[CV] = {
        (for (cv <- seq) yield ConfigValues.CValue(cv.getTrialName, getValueData(cv.getData))).toSet[CV]
      }

      TelemetryEvent(oe.getEventId, oe.getTimestamp, oe.getSource, oe.getPrefix, getValues(oe.getValuesList))
    }

    def apply(bytes: Array[Byte]): TelemetryEvent = {
      TelemetryEvent(Protobuf.EventType.parseFrom(bytes).getTelemetryEvent)
    }
  }

  /**
   * Defines an observe event
   */
  case class ObserveEvent(eventId: String, timestamp: Long, source: String) extends EventType {
    override def toBinary: Array[Byte] = {
      Protobuf.EventType.newBuilder().setObserveEvent(
        Protobuf.ObserveEvent.newBuilder()
          .setEventId(eventId)
          .setTimestamp(timestamp)
          .setSource(source)
      ).build.toByteArray
    }
  }

  object ObserveEvent {
    def apply(oe: Protobuf.ObserveEvent): ObserveEvent =
      ObserveEvent(oe.getEventId, oe.getTimestamp, oe.getSource)

    def apply(bytes: Array[Byte]): ObserveEvent =
      ObserveEvent(Protobuf.EventType.parseFrom(bytes).getObserveEvent)
  }

  // XXX TODO: Add "endless" EventStream? (only neeeded if "pulling" events)
}



