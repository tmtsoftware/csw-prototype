package csw.util.cfg

import java.util.UUID

import scala.compat.Platform
import scala.language.implicitConversions

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
   * @tparam T the subclass of ConfigType
   */
  sealed trait Configuration[T <: Configuration[T]] {
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
     * @return the size of the Map
     */
    def size = data.size

    final def set[A](key: Key.Aux[A], value: A): T = create(data.set(key, value))

    /**
     * Lookup a Key in Map and returns an Option
     * @param key the Key to be used for lookup
     * @return an option value typed to the Key
     */
    final def get(key: Key): Option[key.Value] = data.get(key)

    /**
     * Remove a Key from the Map and return a new Map
     * @param key the Key to be used for removal
     * @return a new T, where T is a ConfigType child
     */
    final def remove(key: Key): T = create(data.remove(key))

    /**
     * Return the value associated with a Key rather than an Option
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
      case (k, v) ⇒ k.name -> v.toString
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

  case class SetupConfig(configKey: ConfigKey, data: ConfigData = ConfigData()) extends Configuration[SetupConfig] with SequenceConfig with ControlConfig {
    override protected def create(data: ConfigData) = SetupConfig(configKey, data)

    override def toString = doToString("SC")
  }

  case class ObserveConfig(configKey: ConfigKey, data: ConfigData = ConfigData()) extends Configuration[ObserveConfig] with SequenceConfig with ControlConfig {
    override protected def create(data: ConfigData) = ObserveConfig(configKey, data)

    override def toString = doToString("OC")
  }

  case class WaitConfig(configKey: ConfigKey, data: ConfigData = ConfigData()) extends Configuration[WaitConfig] with SequenceConfig {
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
     * @param demand the demand state
     * @param current the current state
     * @return true if the demand and current states match (in this case, are equal)
     */
    def defaultMatcher(demand: DemandState, current: CurrentState): Boolean =
      demand.prefix == current.prefix && demand.data == current.data

    case class DemandState(configKey: ConfigKey, data: ConfigData = ConfigData())
        extends Configuration[DemandState] with StateVariable {

      override protected def create(data: ConfigData) = DemandState(configKey, data)

      override def toString = doToString("demand")

      def extKey = DemandState.makeExtKey(prefix)
    }

    object DemandState {
      /**
       * Returns the key used to store the demand state for the given prefix
       */
      def makeExtKey(prefix: String): String = s"demand:$prefix"
    }

    case class CurrentState(configKey: ConfigKey, data: ConfigData = ConfigData())
        extends Configuration[CurrentState] with StateVariable {

      override protected def create(data: ConfigData) = CurrentState(configKey, data)

      override def toString = doToString("current")

      def extKey = CurrentState.makeExtKey(prefix)
    }

    object CurrentState {
      /**
       * Returns the key used to store the current state for the given prefix
       */
      def makeExtKey(prefix: String): String = s"current:$prefix"

      /**
       * Returns the key used to store the current state for the given demand state
       */
      def makeExtKey(demand: DemandState): String = s"current:${demand.prefix}"
    }

  }

  /**
   * Filters
   */
  object ConfigFilters {
    // A filter type for various ConfigData
    type ConfigFilter[A] = A ⇒ Boolean

    def prefixes(configs: Seq[Configuration[_]]): Set[String] = configs.map(_.prefix).toSet

    def onlySetupConfigs(configs: Seq[SequenceConfig]): Seq[SetupConfig] = configs.collect { case ct: SetupConfig ⇒ ct }

    def onlyObserveConfigs(configs: Seq[SequenceConfig]): Seq[ObserveConfig] = configs.collect { case ct: ObserveConfig ⇒ ct }

    def onlyWaitConfigs(configs: Seq[SequenceConfig]): Seq[WaitConfig] = configs.collect { case ct: WaitConfig ⇒ ct }

    val prefixStartsWithFilter: String ⇒ ConfigFilter[Configuration[_]] = query ⇒ sc ⇒ sc.prefix.startsWith(query)
    val prefixContainsFilter: String ⇒ ConfigFilter[Configuration[_]] = query ⇒ sc ⇒ sc.prefix.contains(query)
    val prefixIsSubsystem: Subsystem ⇒ ConfigFilter[Configuration[_]] = query ⇒ sc ⇒ sc.subsystem.equals(query)

    def prefixStartsWith(query: String, configs: Seq[Configuration[_]]): Seq[Configuration[_]] = configs.filter(prefixStartsWithFilter(query))

    def prefixContains(query: String, configs: Seq[Configuration[_]]): Seq[Configuration[_]] = configs.filter(prefixContainsFilter(query))

    def prefixIsSubsystem(query: Subsystem, configs: Seq[Configuration[_]]): Seq[Configuration[_]] = configs.filter(prefixIsSubsystem(query))
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
    val runId: UUID = UUID.randomUUID()
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

  final case class SetupConfigArg(info: ConfigInfo, configs: Seq[SetupConfig]) extends SequenceConfigArg with ControlConfigArg

  object SetupConfigArg {
    def apply(configs: SetupConfig*)(implicit info: ConfigInfo): SetupConfigArg = SetupConfigArg(info, configs.toSeq)
  }

  final case class ObserveConfigArg(info: ConfigInfo, configs: Seq[ObserveConfig]) extends SequenceConfigArg with ControlConfigArg

  object ObserveConfigArg {
    def apply(configs: ObserveConfig*)(implicit info: ConfigInfo): ObserveConfigArg = ObserveConfigArg(info, configs.toSeq)
  }

  final case class WaitConfigArg(info: ConfigInfo, config: WaitConfig) extends SequenceConfigArg

  object WaitConfigArg {
    def apply(wc: WaitConfig)(implicit info: ConfigInfo): WaitConfigArg = WaitConfigArg(info, wc)
  }

  /**
   * Contains a list of configs that can be sent to a sequencer
   */
  final case class ConfigArgList(configs: Seq[SequenceConfig])

  // XXX Allan: Should be part of command service actor messages?
  //
  //  // For getting device configuration
  //  final case class ConfigQuery(configs: Seq[SetupConfig])
  //
  //  sealed trait ConfigQueryResponse
  //  final case class QuerySuccess(configs: Seq[SetupConfig]) extends ConfigQueryResponse
  //  final case class QueryFailure(reason: String) extends ConfigQueryResponse
}

object Events {

  import Configurations.{ ConfigKey, Configuration }

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
   * Defines the possible event types, for use in serialization and deserialization
   */
  sealed trait EventType {
    val code: String

    override def toString = code
  }

  case object StatusEventType extends EventType {
    val code = "status event"
  }

  case object ObserveEventType extends EventType {
    val code = "observe event"
  }

  case object SystemEventType extends EventType {
    val code = "system event"
  }

  /**
   * A concrete intermediate event class that can be used to serialize and deserialize events.
   */
  final case class SerializableEvent(eventType: EventType, info: EventInfo, data: ConfigData) {
    def getEvent: Event[_] = eventType match {
      case StatusEventType  ⇒ StatusEvent(info, data)
      case ObserveEventType ⇒ ObserveEvent(info, data)
      case SystemEventType  ⇒ SystemEvent(info, data)
    }
  }

  /**
   * Marker trait for event configurations
   */
  sealed trait Event[T <: Event[T]] extends Configuration[T] {
    self: T ⇒

    def eventType: EventType

    def info: EventInfo

    override def configKey = info.source

    def source = configKey.prefix

    def rawTime = info.time

    def eventId = info.eventId

    def obsId = info.obsId

    override def toString = doToString(eventType.code)

    def toByteArray: Array[Byte] = {
      import scala.pickling.Defaults._
      import scala.pickling.binary._
      val se = SerializableEvent(eventType, info, data)
      se.pickle.value
    }
  }

  object Event {
    def apply(ar: Array[Byte]): Event[_] = {
      import scala.pickling.Defaults._
      import scala.pickling.binary._
      val se = ar.unpickle[SerializableEvent]
      se.getEvent
    }
  }

  case class StatusEvent(info: EventInfo, data: ConfigData = ConfigData()) extends Event[StatusEvent] {
    override def eventType = StatusEvent.eventType

    override protected def create(data: ConfigData) = StatusEvent(info, data)
  }

  object StatusEvent {
    val eventType = StatusEventType

    def apply(prefix: String, time: EventTime): StatusEvent = StatusEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): StatusEvent = StatusEvent(EventInfo(prefix, time, Some(obsId)))
  }

  case class ObserveEvent(info: EventInfo, data: ConfigData = ConfigData()) extends Event[ObserveEvent] {
    override def eventType = ObserveEvent.eventType

    override protected def create(data: ConfigData) = ObserveEvent(info, data)
  }

  object ObserveEvent {
    val eventType = ObserveEventType

    def apply(prefix: String, time: EventTime): ObserveEvent = ObserveEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): ObserveEvent = ObserveEvent(EventInfo(prefix, time, Some(obsId)))
  }

  case class SystemEvent(info: EventInfo, data: ConfigData = ConfigData()) extends Event[SystemEvent] {
    override def eventType = SystemEvent.eventType

    override protected def create(data: ConfigData) = SystemEvent(info, data)
  }

  object SystemEvent {
    val eventType = SystemEventType

    def apply(prefix: String, time: EventTime): SystemEvent = SystemEvent(EventInfo(prefix, time))

    def apply(prefix: String, time: EventTime, obsId: ObsId): SystemEvent = SystemEvent(EventInfo(prefix, time, Some(obsId)))
  }

  // Experiemental -- one way to keep from adding methods into each
  // Just testing
  def toStatusEvent(prefix: String) = (time: EventTime) ⇒ {
    val configKey: ConfigKey = prefix
    val evInfo = EventInfo(prefix, time)
    StatusEvent(evInfo)
  }

  trait EventAccess[A] {
    def subsystem(in: A): Subsystem

    def source(in: A): String

    def rawTime(in: A): EventTime

    def eventId(in: A): UUID

    def obsId(in: A): Option[ObsId]
  }

  def subsystem[A](in: A)(implicit ea: EventAccess[A]): Subsystem = ea.subsystem(in)

  def source[A](in: A)(implicit ea: EventAccess[A]): String = ea.source(in)

  def rawTime[A](in: A)(implicit ea: EventAccess[A]): EventTime = ea.rawTime(in)

  def eventId[A](in: A)(implicit ea: EventAccess[A]): UUID = ea.eventId(in)

  def obsID[A](in: A)(implicit ea: EventAccess[A]): Option[ObsId] = ea.obsId(in)

  implicit object StatusEventAccess extends EventAccess[StatusEvent] {
    def subsystem(in: StatusEvent) = in.subsystem

    def source(in: StatusEvent) = in.prefix

    def rawTime(in: StatusEvent) = in.info.time

    def eventId(in: StatusEvent) = in.info.eventId

    def obsId(in: StatusEvent) = in.info.obsId
  }

  implicit object ObserveEventAccess extends EventAccess[ObserveEvent] {
    def subsystem(in: ObserveEvent) = in.subsystem

    def source(in: ObserveEvent) = in.prefix

    def rawTime(in: ObserveEvent) = in.info.time

    def eventId(in: ObserveEvent) = in.info.eventId

    def obsId(in: ObserveEvent) = in.info.obsId
  }

  implicit object SystemEventAccess extends EventAccess[SystemEvent] {
    def subsystem(in: SystemEvent) = in.subsystem

    def source(in: SystemEvent) = in.prefix

    def rawTime(in: SystemEvent) = in.info.time

    def eventId(in: SystemEvent) = in.info.eventId

    def obsId(in: SystemEvent) = in.info.obsId
  }

}
