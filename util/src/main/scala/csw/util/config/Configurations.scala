package csw.util.config

import java.util.Optional

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

import scala.language.implicitConversions
import scala.annotation.varargs
import scala.compat.java8.OptionConverters._
import scala.collection.JavaConverters._

/**
 * TMT Source Code: 5/22/16.
 */
object Configurations {

  /**
   * A top level key for a configuration: combines subsystem and the subsystem's prefix
   *
   * @param subsystem the subsystem that is the target of the config
   * @param prefix    the subsystem's prefix
   */
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

  type ConfigData = Set[Item[_, _]]

  /**
   * The base trait for various configuration types (command configurations or events)
   *
   * @tparam T the subclass of ConfigType
   */
  trait ConfigType[T <: ConfigType[T]] {
    self: T ⇒

    /**
     * Returns an object providing the subsystem and prefix for the config
     */
    def configKey: ConfigKey

    /**
     * Holds the items for this config
     */
    protected def items: ConfigData

    /**
     * The number of items in this configuration
     *
     * @return the number of items in the configuration
     */
    def size = items.size

    /**
     * Adds an item to the config
     *
     * @param item the item to add
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this config with the given item added
     */
    def add[S, J](item: Item[S, J]): T = {
      val configRemoved: T = removeByKeyname(item.keyName)
      create(configRemoved.items + item)
    }

    /**
     * Sets the given key to the given values
     *
     * @param key   the key, which also contains the value type
     * @param units the units for the values
     * @param v     one or more values
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this object with the key set to the given values
     */
    def set[S, J](key: Key[S, J], units: Units, v: S*): T = {
      val newItem = key.set(v: _*).withUnits(units)
      add(newItem)
    }

    /**
     * Sets the given key to the given values
     *
     * @param key the key, which also contains the value type
     * @param v   one or more values
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this object with the key set to the given values
     */
    def set[S, J](key: Key[S, J], v: S*): T = {
      val newItem = key.set(v: _*)
      add(newItem)
    }

    /**
     * Sets the given key to the given values
     *
     * @param key   the key, which also contains the value type
     * @param v     a vector with the values
     * @param units the units for the values
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this object with the key set to the given values
     */
    def set[S, J](key: Key[S, J], v: Vector[S], units: Units = NoUnits): T = {
      val newItem = key.set(v).withUnits(units)
      add(newItem)
    }

    /**
     * Returns the item for the key, if found, otherwise None
     *
     * @param key the Key to be used for lookup
     * @return the item for the key, if found
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def get[S, J](key: Key[S, J]): Option[Item[S, J]] = getByKeyname[S, J](key.keyName)

    /**
     * Returns the value for the key, if found, otherwise None
     *
     * @param key the Key to be used for lookup
     * @return the value for the key, if found
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def get[S, J](key: Key[S, J], index: Int): Option[S] = get(key).get.get(index)

    /**
     * Returns the first or default value for the given key, throwing an exception if the key is not present
     *
     * @param key the key to be used for lookup
     * @return the first or default value for the given key
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def value[S, J](key: Key[S, J]): S = get(key).get.value

    /**
     * Returns the value for the given key at the given index, throwing an exception if the key or value is not present
     *
     * @param key   the key to use
     * @param index the index in the key's values
     * @return the value for the given key at the given index
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def value[S, J](key: Key[S, J], index: Int): S = get(key).get.value(index)

    /**
     * Returns the values for the given key, throwing an exception if the key is not present
     *
     * @param key the key to be used for lookup
     * @return the values for the given key
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def values[S, J](key: Key[S, J]): Vector[S] = get(key).get.values

    /**
     * Java API: Returns a new instance of this config with the values for the given key set to the given values
     *
     * @param key   the key to use
     * @param units the units for the values
     * @param v     one or more values
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this object with the key set to the given value
     */
    def jset[S, J](key: Key[S, J], units: Units, v: J*): T = {
      val newItem = key.jset(v: _*).withUnits(units)
      add(newItem)
    }

    /**
     * Java API: Returns a new instance of this config with the values for the given key set to the given values
     *
     * @param key the key to use
     * @param v   one or more values
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this object with the key set to the given value
     */
    def jset[S, J](key: Key[S, J], v: J*): T = {
      val newItem = key.jset(v: _*)
      add(newItem)
    }

    /**
     * Java API: Returns a new instance of this config with the values for the given key set to the given values
     *
     * @param key   the key to use
     * @param units the units for the values
     * @param v     a list with the values
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new instance of this object with the key set to the given value
     */
    def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): T = jset(key, units, v.asScala: _*)

    /**
     * Java API: Returns a new instance of this config with the values for the given key set to the given values
     *
     * @param key the key to use
     * @param v   a list with the values
     * @return a new instance of this object with the key set to the given value
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def jset[S, J](key: Key[S, J], v: java.util.List[J]): T = jset(key, v.asScala: _*)

    /**
     * Java API: Returns the value for the given key at the given index, throwing an exception if the key or value is not present
     *
     * @param key   the key to use
     * @param index the index in the key's values
     * @return the value for the given key at the given index
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def jvalue[S, J](key: Key[S, J], index: Int): J = get(key).get.jvalue(index)

    /**
     * Java API: Returns the first or default value for the given key, throwing an exception if the key is not present
     *
     * @param key the key to use
     * @return the first or default value for the given key
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def jvalue[S, J](key: Key[S, J]): J = get(key).get.jvalue

    /**
     * Java API: Returns the first or default value for the given key, throwing an exception if the key is not present
     *
     * @param key the key to use
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return the first or default value for the given key
     */
    def jvalues[S, J](key: Key[S, J]): java.util.List[J] = get(key).get.jvalues

    /**
     * Java API: Returns the item for the key, if found, otherwise None
     *
     * @param key the Key to be used for lookup
     * @return the item for the key, if found
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = get(key).asJava

    /**
     * Returns the value for the key, if found, otherwise None
     *
     * @param key the Key to use
     * @return the value for the key, if found
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def jget[S, J](key: Key[S, J], index: Int): Optional[J] = (if (index >= 0 && index < size) Some(jvalue(key, index)) else None).asJava

    /**
     * Returns true if the key exists in the config
     *
     * @param key the key to check for
     * @return true if the key is found
     * @tparam S the Scala value type
     * @tparam J the Java value type
     */
    def exists[S, J](key: Key[S, J]): Boolean = get(key).isDefined

    /**
     * Remove a Key from the Map and return a new Map
     *
     * @param key the Key to be used for removal
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return a new T, where T is a ConfigType child with the key removed or identical if the key is not present
     */
    def remove[S, J](key: Key[S, J]): T = removeByKeyname(key.keyName)

    private def removeByKeyname(keyname: String): T = {
      val f = getByKeyname(keyname)
      f match {
        case Some(item) ⇒ create(items.-(item))
        case None       ⇒ this
      }
    }

    private def getByKeyname[S, J](keyname: String): Option[Item[S, J]] =
      items.find(_.keyName == keyname).asInstanceOf[Option[Item[S, J]]]

    /**
     * Return the value associated with a Key rather than an Option
     *
     * @param key the Key to be used for lookup
     * @tparam S the Scala value type
     * @tparam J the Java value type
     * @return the item associated with the Key or a NoSuchElementException if the key does not exist
     */
    final def apply[S, J](key: Key[S, J]): Seq[S] = get[S, J](key).get.values

    /**
     * The subsystem for the config
     */
    final def subsystem: Subsystem = configKey.subsystem

    /**
     * The prefix for the config
     */
    final def prefix: String = configKey.prefix

    /**
     * Method called by subclass to create a copy with the same key (or other fields) and new items
     */
    protected def create(data: ConfigData): T

    protected def dataToString = items.mkString("(", ",", ")")

    protected def doToString(kind: String) = s"$kind[$subsystem, $prefix]$dataToString"

    /**
     * Returns true if the data contains the given key
     */
    def contains(key: Key[_, _]): Boolean = items.exists(_.keyName == key.keyName)

    /**
     * Returns a set containing the names of any of the given keys that are missing in the data
     *
     * @param keys one or more keys
     */
    def missingKeys(keys: Key[_, _]*): Set[String] = {
      val argKeySet = keys.map(_.keyName).toSet
      val itemsKeySet = items.map(_.keyName)
      argKeySet.diff(itemsKeySet)
    }

    /**
     * Returns a map based on this object where the keys and values are in string format
     * (Could be useful for exporting in a format that other languages can read).
     * Derived classes might want to add values to this map for fixed fields.
     */
    def getStringMap: Map[String, String] = items.map(i ⇒ i.keyName → i.values.map(_.toString).mkString(",")).toMap
  }

  /**
   * Marker trait for sequence configurations
   */
  sealed trait SequenceConfig

  /**
   * Marker trait for control configurations
   */
  sealed trait ControlConfig

  /**
   * A configuration for setting telescope and instrument parameters
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class SetupConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]])
      extends ConfigType[SetupConfig] with SequenceConfig with ControlConfig {

    override def create(data: ConfigData) = SetupConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): SetupConfig = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): SetupConfig = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): SetupConfig = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): SetupConfig = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): SetupConfig = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): SetupConfig = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): SetupConfig = super.remove[S, J](key)

    override def toString = doToString("SC")
  }

  /**
   * A configuration for setting observation parameters
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class ObserveConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]])
      extends ConfigType[ObserveConfig] with SequenceConfig with ControlConfig {

    override def create(data: ConfigData) = ObserveConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): ObserveConfig = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): ObserveConfig = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): ObserveConfig = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): ObserveConfig = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): ObserveConfig = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): ObserveConfig = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): ObserveConfig = super.remove[S, J](key)

    override def toString = doToString("OC")
  }

  /**
   * A configuration indicating a pause in processing
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class WaitConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]])
      extends ConfigType[WaitConfig] with SequenceConfig {

    override def create(data: ConfigData) = WaitConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): WaitConfig = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): WaitConfig = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): WaitConfig = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): WaitConfig = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): WaitConfig = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): WaitConfig = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): WaitConfig = super.remove[S, J](key)

    override def toString = doToString("OC")
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

  /**
   * Combines multiple SetupConfigs together with a ConfigInfo object containing the obsId and runId
   *
   * @param info    contains the obsId, runId
   * @param configs one or more SetupConfigs
   */
  final case class SetupConfigArg(info: ConfigInfo, configs: SetupConfig*) extends SequenceConfigArg with ControlConfigArg {
    /**
     * Java API: Returns the list of configs
     */
    def jconfigs: java.util.List[SetupConfig] = configs.asJava
  }

  object SetupConfigArg {
    /**
     * Creates a SetupConfigArg assuming that an implicit ConfigInfo is in scope
     *
     * @param configs the configs to include in the object
     * @param info the implicit config info
     * @return a new object containing the configs and info
     */
    def apply(configs: SetupConfig*)(implicit info: ConfigInfo): SetupConfigArg = SetupConfigArg(info, configs: _*)
  }

  /**
   * For the Java API
   *
   * @param obsId   the obs id string
   * @param configs one or more configs
   * @return a new SetupConfigArg containing the obsId and configs
   */
  @varargs
  def createSetupConfigArg(obsId: String, configs: SetupConfig*): SetupConfigArg = SetupConfigArg(ConfigInfo(ObsId(obsId)), configs: _*)

  /**
   * Combines multiple ObserveConfigs together with a ConfigInfo object containing the obsId and runId
   *
   * @param info    contains the obsId, runId
   * @param configs one or more ObserveConfigs
   */
  final case class ObserveConfigArg(info: ConfigInfo, configs: ObserveConfig*) extends SequenceConfigArg with ControlConfigArg {
    /**
     * Java API: Returns the list of configs
     */
    def jconfigs: java.util.List[ObserveConfig] = configs.asJava

    @varargs
    def withConfigs(configs: ObserveConfig*): ObserveConfigArg = ObserveConfigArg(info, configs: _*)
  }

  object ObserveConfigArg {
    /**
     * Creates an ObserveConfigArg assuming that an implicit ConfigInfo is in scope
     *
     * @param configs the configs to include in the object
     * @param info the implicit config info
     * @return a new object containing the configs and info
     */
    def apply(configs: ObserveConfig*)(implicit info: ConfigInfo): ObserveConfigArg = ObserveConfigArg(info, configs: _*)
  }

  /**
   * For the Java API
   *
   * @param obsId   the obs id string
   * @param configs one or more configs
   * @return a new ObserveConfigArg containing the obsId and configs
   */
  @varargs
  def createObserveConfigArg(obsId: String, configs: ObserveConfig*): ObserveConfigArg = ObserveConfigArg(ConfigInfo(ObsId(obsId)), configs: _*)

  /**
   * Combines a WaitConfig with a ConfigInfo object containing the obsId and runId
   *
   * @param info   contains the obsId, runId
   * @param config the WaitConfig
   */
  final case class WaitConfigArg(info: ConfigInfo, config: WaitConfig) extends SequenceConfigArg

  @varargs
  object WaitConfigArg {
    def apply(wc: WaitConfig)(implicit info: ConfigInfo): WaitConfigArg = WaitConfigArg(info, wc)
  }

  /**
   * Contains a list of configs that can be sent to a sequencer
   */
  final case class ConfigArgList(configs: Seq[SequenceConfig])

}

