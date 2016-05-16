package javacsw.util.cfg

import java.util.{Optional, OptionalDouble, OptionalInt}

import csw.util.cfg.StateVariable.{CurrentState, DemandState}
import csw.util.cfg.Configurations._
import csw.util.cfg.Events._
import csw.util.cfg._

import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._

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

  def createCurrentState(prefix: String): JCurrentState = JCurrentState(CurrentState(prefix))

  def createCurrentState(config: SetupConfig): JCurrentState = JCurrentState(CurrentState(config.prefix, config.data))

  def createDemandState(prefix: String): JDemandState = JDemandState(DemandState(prefix))

  def createDemandState(config: SetupConfig): JDemandState = JDemandState(DemandState(config.prefix, config.data))

  // --- Used by assemblies --

  @annotation.varargs
  def createSetupConfigArg(info: ConfigInfo, configs: SetupConfig*): JSetupConfigArg = JSetupConfigArg(SetupConfigArg(configs.toSeq: _*)(info))

  @annotation.varargs
  def createObserveConfigArg(info: ConfigInfo, configs: ObserveConfig*): JObserveConfigArg = JObserveConfigArg(ObserveConfigArg(configs.toSeq: _*)(info))

  /**
   * Common getter methods for Java APIs
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

    /**
     * @return the prefix for the config
     */
    def getPrefix: String = configType.prefix
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

/**
 * Java wrapper for CurrentState
 *
 * @param configType the underlying config
 */
case class JCurrentState(configType: CurrentState) extends JConfigurations.ConfigGetters[CurrentState] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Any): JCurrentState = {
    JCurrentState(configType.jset(key, value))
  }
}

/**
 * Java wrapper for DemandState
 *
 * @param configType the underlying config
 */
case class JDemandState(configType: DemandState) extends JConfigurations.ConfigGetters[DemandState] {
  /**
   * Returns a new instance of this object with the given key set to the given value.
   *
   * @param key   the key, which also defines the expected value type
   * @param value the value, which must be of the type Key#Value
   * @return a new instance with key set to value
   */
  def set(key: Key, value: Any): JDemandState = {
    JDemandState(configType.jset(key, value))
  }
}

// --- These are used by Assemblies --

/**
 * Java wrapper for SetupConfigArg
 *
 * @param setupConfigArg the underlying SetupConfigArg object
 */
case class JSetupConfigArg(setupConfigArg: SetupConfigArg) {
  /**
   * @return the list of configs in the object
   */
  def getConfigs: java.util.List[JSetupConfig] = setupConfigArg.configs.map(JSetupConfig).asJava

  /**
   * @return an object containing config information
   */
  def getInfo: ConfigInfo = setupConfigArg.info
}

/**
 * Java wrapper for ObserveConfigArg
 *
 * @param observeConfigArg the underlying ObserveConfigArg object
 */
case class JObserveConfigArg(observeConfigArg: ObserveConfigArg) {
  /**
   * @return the list of configs in the object
   */
  def getConfigs: java.util.List[JObserveConfig] = observeConfigArg.configs.map(JObserveConfig).asJava

  /**
   * @return an object containing config information
   */
  def getInfo: ConfigInfo = observeConfigArg.info
}

