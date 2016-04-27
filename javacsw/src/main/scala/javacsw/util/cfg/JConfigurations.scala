package javacsw.util.cfg

import java.util.{Optional, OptionalDouble, OptionalInt}

import csw.util.cfg.Events.{EventTime, ObserveEvent}
import csw.util.cfg.{Key, ObsId}

import scala.compat.java8.OptionConverters._

/**
 * Java API to Scala Configurations classes (Experimental)
 */
object JConfigurations {
  def createObserveEvent(prefix: String, time: EventTime): JObserveEvent = JObserveEvent(ObserveEvent(prefix, time))

  def createObserveEvent(prefix: String, time: EventTime, obsId: ObsId): JObserveEvent = JObserveEvent(ObserveEvent(prefix, time, obsId))

}

/**
 * Java wrapper for ObserveEvent
 *
 * @param oe the underlying ObserveEvent
 */
case class JObserveEvent(oe: ObserveEvent) {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Object): JObserveEvent = {
    JObserveEvent(oe.jset(key, value))
  }

  /**
   * Returns the value for the given key.
   *
   * @param key the key, which also defines the expected value type
   * @return the value for key if found, otherwise
   */
  def get(key: Key): Optional[Object] = oe.get(key).map(_.asInstanceOf[Object]).asJava

  def getAsDouble(key: Key): OptionalDouble = oe.get(key).map(_.asInstanceOf[Double]).asPrimitive

  def getAsInteger(key: Key): OptionalInt = oe.get(key).map(_.asInstanceOf[Int]).asPrimitive

  def getAsString(key: Key): Optional[String] = oe.get(key).map(_.asInstanceOf[String]).asJava
}
