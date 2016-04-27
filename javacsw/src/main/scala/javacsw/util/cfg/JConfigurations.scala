package javacsw.util.cfg

import java.util.{Optional, OptionalDouble, OptionalInt}

import csw.util.cfg.Configurations.ConfigType
import csw.util.cfg.Events.{EventTime, EventType, ObserveEvent}
import csw.util.cfg.{Key, ObsId}

import scala.compat.java8.OptionConverters._

/**
 * Java API to Scala Configurations classes (Experimental)
 */
object JConfigurations {
  def createObserveEvent(prefix: String, time: EventTime): JObserveEvent = JObserveEvent(ObserveEvent(prefix, time))

  def createObserveEvent(prefix: String, time: EventTime, obsId: ObsId): JObserveEvent = JObserveEvent(ObserveEvent(prefix, time, obsId))

  /**
    * Common getter methods for Java APIs
    * @tparam A the type of the config
    */
  trait ConfigGetters[A <: ConfigType[A]] {
    val configType: ConfigType[A]
    /**
      * Returns the value for the given key.
      *
      * @param key the key, which also defines the expected value type
      * @return the value for key if found, otherwise
      */
    def get(key: Key): Optional[Object] = configType.get(key).map(_.asInstanceOf[Object]).asJava

    def getAsDouble(key: Key): OptionalDouble = configType.get(key).map(_.asInstanceOf[Double]).asPrimitive

    def getAsInteger(key: Key): OptionalInt = configType.get(key).map(_.asInstanceOf[Int]).asPrimitive

    def getAsString(key: Key): Optional[String] = configType.get(key).map(_.asInstanceOf[String]).asJava
  }
}

/**
 * Java wrapper for ObserveEvent
 *
 * @param configType the underlying config
 */
case class JObserveEvent(configType: ObserveEvent) extends JConfigurations.ConfigGetters[ObserveEvent] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Object): JObserveEvent = {
    JObserveEvent(configType.jset(key, value))
  }
}

