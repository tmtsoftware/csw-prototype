package javacsw.util.cfg

import java.util.{ Optional, OptionalDouble, OptionalInt }

import csw.util.cfg.Configurations._
import csw.util.cfg.Events._
import csw.util.cfg._

import scala.compat.java8.OptionConverters._

/**
 * Java API to Scala Configurations classes
 */
object JConfigurations {
  def createObserveEvent(prefix: String): JObserveEvent = JObserveEvent(ObserveEvent(prefix))

  def createObserveEvent(prefix: String, time: EventTime): JObserveEvent = JObserveEvent(ObserveEvent(prefix, time))

  def createObserveEvent(prefix: String, obsId: ObsId): JObserveEvent = JObserveEvent(ObserveEvent(prefix, EventTime.toCurrent, obsId))

  def createObserveEvent(prefix: String, time: EventTime, obsId: ObsId): JObserveEvent = JObserveEvent(ObserveEvent(prefix, time, obsId))

  def createStatusEvent(prefix: String): JStatusEvent = JStatusEvent(StatusEvent(prefix))

  def createStatusEvent(prefix: String, time: EventTime): JStatusEvent = JStatusEvent(StatusEvent(prefix, time))

  def createStatusEvent(prefix: String, obsId: ObsId): JStatusEvent = JStatusEvent(StatusEvent(prefix, EventTime.toCurrent, obsId))

  def createStatusEvent(prefix: String, time: EventTime, obsId: ObsId): JStatusEvent = JStatusEvent(StatusEvent(prefix, time, obsId))

  def createSystemEvent(prefix: String): JSystemEvent = JSystemEvent(SystemEvent(prefix))

  def createSystemEvent(prefix: String, time: EventTime): JSystemEvent = JSystemEvent(SystemEvent(prefix, time))

  def createSystemEvent(prefix: String, obsId: ObsId): JSystemEvent = JSystemEvent(SystemEvent(prefix, EventTime.toCurrent, obsId))

  def createSystemEvent(prefix: String, time: EventTime, obsId: ObsId): JSystemEvent = JSystemEvent(SystemEvent(prefix, time, obsId))

  def createSetupConfig(prefix: String): JSetupConfig = JSetupConfig(SetupConfig(prefix))

  def createObserveConfig(prefix: String): JObserveConfig = JObserveConfig(ObserveConfig(prefix))

  def createWaitConfig(prefix: String): JWaitConfig = JWaitConfig(WaitConfig(prefix))

  /**
   * Common getter methods for Java APIs
   *
   * @tparam A the type of the config
   */
  trait ConfigGetters[A <: ConfigType[A]] {
    val configType: ConfigType[A]

    // XXX TODO: Add support for more types of values to java API here
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
  def set(key: Key, value: Any): JObserveEvent = {
    JObserveEvent(configType.jset(key, value))
  }
}

/**
 * Java wrapper for StatusEvent
 *
 * @param configType the underlying config
 */
case class JStatusEvent(configType: StatusEvent) extends JConfigurations.ConfigGetters[StatusEvent] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Any): JStatusEvent = {
    JStatusEvent(configType.jset(key, value))
  }
}

/**
 * Java wrapper for SystemEvent
 *
 * @param configType the underlying config
 */
case class JSystemEvent(configType: SystemEvent) extends JConfigurations.ConfigGetters[SystemEvent] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Any): JSystemEvent = {
    JSystemEvent(configType.jset(key, value))
  }
}

/**
 * Java wrapper for SetupConfig
 *
 * @param configType the underlying config
 */
case class JSetupConfig(configType: SetupConfig) extends JConfigurations.ConfigGetters[SetupConfig] {
  /**
    * Returns a new instance of this object with the given key set to the given value.
    *
    * @param key   the key, which also defines the expected value type
    * @param value the value, which must be of the type Key#Value
    * @return a new instance with key set to value
    */
  def set(key: Key, value: Any): JSetupConfig = {
    JSetupConfig(configType.jset(key, value))
  }
}

/**
 * Java wrapper for ObserveConfig
 *
 * @param configType the underlying config
 */
case class JObserveConfig(configType: ObserveConfig) extends JConfigurations.ConfigGetters[ObserveConfig] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Any): JObserveConfig = {
    JObserveConfig(configType.jset(key, value))
  }
}

/**
 * Java wrapper for WaitConfig
 *
 * @param configType the underlying config
 */
case class JWaitConfig(configType: WaitConfig) extends JConfigurations.ConfigGetters[WaitConfig] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Any): JWaitConfig = {
    JWaitConfig(configType.jset(key, value))
  }
}

