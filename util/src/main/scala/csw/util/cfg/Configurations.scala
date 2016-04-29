package csw.util.cfg

import java.util.UUID

import scala.compat.Platform
import scala.language.implicitConversions
import java.util.{Optional, OptionalDouble, OptionalInt}

import scala.compat.java8.OptionConverters._


object Configurations {

  case class ConfigKey(subsystem: Subsystem, prefix: String) {
    override def toString = s"[$subsystem, $prefix]"
  }

  /**
   * Defines the different types of configurations: setup, observe, wait, ...
   */
  object ConfigKey {
    private val SEPARATOR = '.'

    /**
     * Creates a ConfigKey from the given string
     *
     * @return a ConfigKey object parsed for the subsystem and prefix
     */
    implicit def stringToConfigKey(prefix: String): ConfigKey = {
      assert(prefix != null)
      ConfigKey(subsystem(prefix), prefix)
    }

    private def subsystem(keyText: String): Subsystem = Subsystem.lookup(keyText.splitAt(keyText.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
  }

  /**
   * The base trait for various configuration types whether command configurations or events
   *
   * @tparam T the subclass of ConfigType
   */
  sealed trait ConfigType[T <: ConfigType[T]] {
    self: T ⇒

    /**
     * Returns an object providing the subsystem and prefix for the config
     */
    def configKey: ConfigKey

    /**
     * Holds the typed key/value pairs
     */
    protected def data: ConfigData

    /**
     * The number of key/value pairs in the Map
     *
     * @return the size of the Map
     */
    def size = data.size

    /**
     * Returns a new instance with the value for the given key set to the given value
     *
     * @param key   the key, which also contains the value type
     * @param value the value
     * @tparam A the type of the value
     * @return a new instance of this object with the key set to the given value
     */
    final def set[A](key: Key.Aux[A], value: A): T = create(data.set(key, value))

    /**
      * (For Java API)
      * Returns a new instance with the value for the given key set to the given value
      *
      * @param key   the key, which also contains the value type
      * @param value the value
      * @return a new instance of this object with the key set to the given value
      */
    final def jset(key: Key, value: Any): T = {
      create(data.jset(key, value))
    }

    /**
     * Lookup a Key in Map and returns an Option
     *
     * @param key the Key to be used for lookup
     * @return an option value typed to the Key
     */
    final def get(key: Key): Option[key.Value] = data.get(key)

    /**
      * (For Java API)
      * Lookup a Key in Map and returns an Option
      *
      * @param key the Key to be used for lookup
      * @return an option value
      */
//    final def jget(key: Key): Optional[key.Value] = data.get(key).asJava
    final def jget(key: Key): Optional[Object] = data.get(key).map(_.asInstanceOf[Object]).asJava

    /**
     * Remove a Key from the Map and return a new Map
     *
     * @param key the Key to be used for removal
     * @return a new T, where T is a ConfigType child
     */
    final def remove(key: Key): T = create(data.remove(key))

    /**
     * Return the value associated with a Key rather than an Option
     *
     * @param key the Key to be used for lookup
     * @return the value associated with the Key or a NoSuchElementException if the key does not exist
     */
    final def apply(key: Key) = get(key).get

    /**
     * Returns the set of keys in the data map
     */
    final def getKeys: Set[Key] = data.data.keySet

    /**
     * Returns a map based on this object where the keys and values are in string format
     * (Could be useful for exporting in a format that other languages can read).
     * Derived classes might want to add values to this map for fixed fields.
     */
    def getStringMap: Map[String, String] = data.data.map {
      case (k, v) ⇒ k.name → v.toString
    }

    /**
     * The subsystem for the config
     */
    final def subsystem: Subsystem = configKey.subsystem

    /**
     * The prefix for the config
     */
    final def prefix: String = configKey.prefix

    /**
     * Method called by subclass to create a copy with the same key (or other fields) and new data
     */
    protected def create(data: ConfigData): T

    protected def doToString(kind: String) = s"$kind[$subsystem, $prefix]$data"
  }

  /**
   * Marker trait for sequence configurations
   */
  sealed trait SequenceConfig

  /**
   * Marker trait for control configurations
   */
  sealed trait ControlConfig

  case class SetupConfig(configKey: ConfigKey, data: ConfigData = ConfigData()) extends ConfigType[SetupConfig] with SequenceConfig with ControlConfig {
    override protected def create(data: ConfigData) = SetupConfig(configKey, data)

    override def toString = doToString("SC")
  }

  case class ObserveConfig(configKey: ConfigKey, data: ConfigData = ConfigData()) extends ConfigType[ObserveConfig] with SequenceConfig with ControlConfig {
    override protected def create(data: ConfigData) = ObserveConfig(configKey, data)

    override def toString = doToString("OC")
  }

  case class WaitConfig(configKey: ConfigKey, data: ConfigData = ConfigData()) extends ConfigType[WaitConfig] with SequenceConfig {
    override protected def create(data: ConfigData) = WaitConfig(configKey, data)

    override def toString = doToString("WAIT")
  }

  // --- State Variables ---

  /**
   * Base trait for state variables
   */
  sealed trait StateVariable

  object StateVariable {

    /**
     * Type of a function that returns true if two state variables (demand and current)
     * match (or are close enough, which is implementation dependent)
     */
    type Matcher = (DemandState, CurrentState) ⇒ Boolean

    /**
     * The default matcher for state variables tests for an exact match
     *
     * @param demand  the demand state
     * @param current the current state
     * @return true if the demand and current states match (in this case, are equal)
     */
    def defaultMatcher(demand: DemandState, current: CurrentState): Boolean =
      demand.prefix == current.prefix && demand.data == current.data

    /**
     * A state variable that indicates the ''demand'' or requested state.
     *
     * @param configKey ket for the data
     * @param data      the data
     */
    case class DemandState(configKey: ConfigKey, data: ConfigData = ConfigData())
        extends ConfigType[DemandState] with StateVariable {

      override protected def create(data: ConfigData) = DemandState(configKey, data)

      override def toString = doToString("demand")
    }

    object DemandState {
      /**
       * Converts a SetupConfig to a DemandState
       */
      implicit def apply(config: SetupConfig): DemandState = DemandState(config.prefix, config.data)
    }

    /**
     * A state variable that indicates the ''current'' or actual state.
     *
     * @param configKey ket for the data
     * @param data      the data
     */
    case class CurrentState(configKey: ConfigKey, data: ConfigData = ConfigData())
        extends ConfigType[CurrentState] with StateVariable {

      override protected def create(data: ConfigData) = CurrentState(configKey, data)

      override def toString = doToString("current")
    }

    object CurrentState {
      /**
       * Converts a SetupConfig to a CurrentState
       */
      implicit def apply(config: SetupConfig): CurrentState = CurrentState(config.prefix, config.data)
    }

  }

  /**
   * Filters
   */
  object ConfigFilters {
    // A filter type for various ConfigData
    type ConfigFilter[A] = A ⇒ Boolean

    def prefixes(configs: Seq[ConfigType[_]]): Set[String] = configs.map(_.prefix).toSet

    def onlySetupConfigs(configs: Seq[SequenceConfig]): Seq[SetupConfig] = configs.collect { case ct: SetupConfig ⇒ ct }

    def onlyObserveConfigs(configs: Seq[SequenceConfig]): Seq[ObserveConfig] = configs.collect { case ct: ObserveConfig ⇒ ct }

    def onlyWaitConfigs(configs: Seq[SequenceConfig]): Seq[WaitConfig] = configs.collect { case ct: WaitConfig ⇒ ct }

    val prefixStartsWithFilter: String ⇒ ConfigFilter[ConfigType[_]] = query ⇒ sc ⇒ sc.prefix.startsWith(query)
    val prefixContainsFilter: String ⇒ ConfigFilter[ConfigType[_]] = query ⇒ sc ⇒ sc.prefix.contains(query)
    val prefixIsSubsystem: Subsystem ⇒ ConfigFilter[ConfigType[_]] = query ⇒ sc ⇒ sc.subsystem.equals(query)

    def prefixStartsWith(query: String, configs: Seq[ConfigType[_]]): Seq[ConfigType[_]] = configs.filter(prefixStartsWithFilter(query))

    def prefixContains(query: String, configs: Seq[ConfigType[_]]): Seq[ConfigType[_]] = configs.filter(prefixContainsFilter(query))

    def prefixIsSubsystem(query: Subsystem, configs: Seq[ConfigType[_]]): Seq[ConfigType[_]] = configs.filter(prefixIsSubsystem(query))
  }

  // --- Config args ---

  /**
   * This will include information related to the observation that is related to a configuration.
   * This will grow and develop.
   */
  case class ConfigInfo(obsId: ObsId) {
    /**
     * Unique ID for this configuration
     */
    val runId: RunId = RunId()
  }

  object ConfigInfo {
    implicit def apply(obsId: String): ConfigInfo = ConfigInfo(ObsId(obsId))
  }

  /**
   * A ConfigArg is what is placed in a queue in Command Service.
   * It can be one or more SetupConfigs, one or more ObserveConfigs or a WaitConfig
   * Each ConfigArg includes a ConfigInfo which will contain information about the executing
   * observation.
   */
  sealed trait ConfigArg extends Serializable {
    def info: ConfigInfo
  }

  /**
   * Marker trait for sequence config args
   */
  sealed trait SequenceConfigArg extends ConfigArg

  /**
   * Marker trait for control config args
   */
  sealed trait ControlConfigArg extends ConfigArg

  final case class SetupConfigArg(info: ConfigInfo, configs: SetupConfig*) extends SequenceConfigArg with ControlConfigArg

  object SetupConfigArg {
    def apply(configs: SetupConfig*)(implicit info: ConfigInfo): SetupConfigArg = SetupConfigArg(info, configs: _*)
  }

  final case class ObserveConfigArg(info: ConfigInfo, configs: ObserveConfig*) extends SequenceConfigArg with ControlConfigArg

  object ObserveConfigArg {
    def apply(configs: ObserveConfig*)(implicit info: ConfigInfo): ObserveConfigArg = ObserveConfigArg(info, configs: _*)
  }

  final case class WaitConfigArg(info: ConfigInfo, config: WaitConfig) extends SequenceConfigArg

  object WaitConfigArg {
    def apply(wc: WaitConfig)(implicit info: ConfigInfo): WaitConfigArg = WaitConfigArg(info, wc)
  }

  /**
   * Contains a list of configs that can be sent to a sequencer
   */
  final case class ConfigArgList(configs: Seq[SequenceConfig])

}

/**
 * Defines events used by the event and telemetry services
 */
object Events {

  import Configurations.{ConfigKey, ConfigType}

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

  case class StatusEvent(info: EventInfo, data: ConfigData = ConfigData()) extends EventType[StatusEvent] {
    override protected def create(data: ConfigData) = StatusEvent(info, data)

    override def toString = doToString("StatusEvent")
  }

  object StatusEvent {
    def apply(prefix: String, time: EventTime): StatusEvent = StatusEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): StatusEvent = StatusEvent(EventInfo(prefix, time, Some(obsId)))
  }

  case class ObserveEvent(info: EventInfo, data: ConfigData = ConfigData()) extends EventType[ObserveEvent] with EventServiceEvent {
    override protected def create(data: ConfigData) = ObserveEvent(info, data)

    override def toString = doToString("ObserveEvent")
  }

  object ObserveEvent {
    def apply(prefix: String, time: EventTime): ObserveEvent = ObserveEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): ObserveEvent = ObserveEvent(EventInfo(prefix, time, Some(obsId)))
  }

  case class SystemEvent(info: EventInfo, data: ConfigData = ConfigData()) extends EventType[SystemEvent] with EventServiceEvent {
    override protected def create(data: ConfigData) = SystemEvent(info, data)

    override def toString = doToString("SystemEvent")
  }

  object SystemEvent {
    def apply(prefix: String, time: EventTime): SystemEvent = SystemEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): SystemEvent = SystemEvent(EventInfo(prefix, time, Some(obsId)))
  }

}
