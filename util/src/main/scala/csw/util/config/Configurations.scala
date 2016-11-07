package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.language.implicitConversions

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

    /**
     * Creates a ConfigKey from the given string
     *
     * @return a ConfigKey object parsed for the subsystem and prefix
     */
    def this(prefix: String) {
      this(ConfigKey.subsystem(prefix), prefix)
    }

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
    implicit def stringToConfigKey(prefix: String): ConfigKey = new ConfigKey(prefix)

    private def subsystem(keyText: String): Subsystem = {
      assert(keyText != null)
      Subsystem.lookup(keyText.splitAt(keyText.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
    }
  }

  type ConfigData = Set[Item[_]]

  /**
   * A trait to be mixed in with ConfigType that provides a configKey item, subsystem and prefix
   */
  trait ConfigKeyType {
    self: ConfigType[_] =>
    /**
     * Returns an object providing the subsystem and prefix for the config
     */
    def configKey: ConfigKey

    /**
     * The subsystem for the config
     */
    final def subsystem: Subsystem = configKey.subsystem

    /**
     * The prefix for the config
     */
    final def prefix: String = configKey.prefix

    override def toString = s"$typeName[$subsystem, $prefix]$dataToString"
  }

  /**
   * The base trait for various configuration types (command configurations or events)
   *
   * @tparam T the subclass of ConfigType
   */
  trait ConfigType[T <: ConfigType[T]] {
    self: T =>

    /**
     * A name identifying the type of config, such as "setup", "observe".
     * This is used in the JSON and toString output.
     */
    def typeName: String = getClass.getSimpleName

    /**
     * Holds the items for this config
     */
    def items: ConfigData

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
     * @tparam I the Item type
     * @return a new instance of this config with the given item added
     */
    def add[I <: Item[_]](item: I): T = doAdd(this, item)

    private def doAdd[I <: Item[_]](c: T, item: I): T = {
      val configRemoved: T = removeByKeyname(c, item.keyName)
      create(configRemoved.items + item)
    }

    /**
     * Adds several items to the config
     *
     * @param itemsToAdd the list of items to add to the configuration
     * @tparam I must be a subclass of Item
     * @return a new instance of this config with the given item added
     */
    def madd[I <: Item[_]](itemsToAdd: I*): T = itemsToAdd.foldLeft(this)((c, item) => doAdd(c, item))

    /**
     * Returns an Option with the item for the key if found, otherwise None
     *
     * @param key the Key to be used for lookup
     * @return the item for the key, if found
     * @tparam S the Scala value type
     * @tparam I the item type for the Scala value S
     */
    def get[S, I <: Item[S]](key: Key[S, I]): Option[I] = getByKeyname[I](items, key.keyName)

    /**
     * Returns an Option with the item for the key if found, otherwise None. Access with keyname rather
     * than Key
     * @param keyName the keyname to be used for the lookup
     * @tparam I the value type
     */
    def getByName[I <: Item[_]](keyName: String): Option[I] = getByKeyname[I](items, keyName)

    def find[I <: Item[_]](item: I): Option[I] = getByKeyname[I](items, item.keyName)

    /**
     * Return the item associated with a Key rather than an Option
     *
     * @param key the Key to be used for lookup
     * @tparam S the Scala value type
     * @tparam I the Item type associated with S
     * @return the item associated with the Key or a NoSuchElementException if the key does not exist
     */
    final def apply[S, I <: Item[S]](key: Key[S, I]): I = get(key).get

    /**
     * Returns the actual item associated with a key
     *
     * @param key the Key to be used for lookup
     * @tparam S the Scala value type
     * @tparam I the Item type associated with S
     * @return the item associated with the key or a NoSuchElementException if the key does not exist
     */
    final def item[S, I <: Item[S]](key: Key[S, I]): I = get(key).get

    /**
     * Returns true if the key exists in the config
     *
     * @param key the key to check for
     * @return true if the key is found
     * @tparam S the Scala value type
     * @tparam I the type of the Item associated with the key
     */
    def exists[S, I <: Item[S]](key: Key[S, I]): Boolean = get(key).isDefined

    /**
     * Remove an item from the config by key
     *
     * @param key the Key to be used for removal
     * @tparam S the Scala value type
     * @tparam I the item type used with Scala type S
     * @return a new T, where T is a ConfigType child with the key removed or identical if the key is not present
     */
    def remove[S, I <: Item[S]](key: Key[S, I]): T = removeByKeyname(this, key.keyName) //doRemove(this, key)

    /**
     * Removes an item based on the item
     * @param item to be removed from the config
     * @tparam I the type of the item to be removed
     * @return a new T, where T is a ConfigType child with the item removed or identical if the item is not present
     */
    def remove[I <: Item[_]](item: I): T = removeByItem(this, item)

    /**
     * Function removes an item from the config c based on keyname
     * @param c the config to remove from
     * @param keyname the key name of the item to remove
     * @tparam I the Item type
     * @return a new T, where T is a ConfigType child with the item removed or identical if the item is not present
     */
    private def removeByKeyname[I <: Item[_]](c: ConfigType[T], keyname: String): T = {
      val f: Option[I] = getByKeyname(c.items, keyname)
      f match {
        case Some(item) => create(c.items.-(item))
        case None       => c.asInstanceOf[T] //create(c.items) also works
      }
    }

    /**
     * Function removes an item from the config c based on item content
     * @param c the config to remove from
     * @param itemIn the item that should be removed
     * @tparam I the Item type
     * @return a new T, where T is a ConfigType child with the item removed or identical if the item is not presen
     */
    private def removeByItem[I <: Item[_]](c: ConfigType[T], itemIn: I): T = {
      val f: Option[I] = getByItem(c.items, itemIn)
      f match {
        case Some(item) => create(c.items.-(item))
        case None       => c.asInstanceOf[T]
      }
    }

    // Function to find an item by keyname - made public to enable matchers
    private def getByKeyname[I](itemsIn: ConfigData, keyname: String): Option[I] =
      itemsIn.find(_.keyName == keyname).asInstanceOf[Option[I]]

    // Function to find an item by item
    private def getByItem[I](itemsIn: ConfigData, item: Item[_]): Option[I] =
      itemsIn.find(_.equals(item)).asInstanceOf[Option[I]]

    /**
     * Method called by subclass to create a copy with the same key (or other fields) and new items
     */
    protected def create(data: ConfigData): T

    protected def dataToString = items.mkString("(", ",", ")")

    override def toString = s"$typeName$dataToString"

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
     * java API: Returns a set containing the names of any of the given keys that are missing in the data
     *
     * @param keys one or more keys
     */
    @varargs
    def jMissingKeys(keys: Key[_, _]*): java.util.Set[String] = missingKeys(keys: _*).asJava

    /**
     * Returns a map based on this object where the keys and values are in string format
     * (Could be useful for exporting in a format that other languages can read).
     * Derived classes might want to add values to this map for fixed fields.
     */
    def getStringMap: Map[String, String] = items.map(i => i.keyName -> i.values.map(_.toString).mkString(",")).toMap
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
  case class SetupConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]])
      extends ConfigType[SetupConfig] with ConfigKeyType with SequenceConfig with ControlConfig {

    override def create(data: ConfigData) = SetupConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): SetupConfig = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): SetupConfig = super.remove(key)
  }

  /**
   * A configuration for setting observation parameters
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class ObserveConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]])
      extends ConfigType[ObserveConfig] with ConfigKeyType with SequenceConfig with ControlConfig {

    override def create(data: ConfigData) = ObserveConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): ObserveConfig = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): ObserveConfig = super.remove(key)
  }

  /**
   * A configuration indicating a pause in processing
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class WaitConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]])
      extends ConfigType[WaitConfig] with ConfigKeyType with SequenceConfig {

    override def create(data: ConfigData) = WaitConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[I <: Item[_]](item: I): WaitConfig = super.add(item)

    override def remove[S, I <: Item[S]](key: Key[S, I]): WaitConfig = super.remove(key)
  }

  /**
   * Filters
   */
  object ConfigFilters {
    // A filter type for various ConfigData
    type ConfigFilter[A] = A => Boolean

    def prefixes(configs: Seq[ConfigKeyType]): Set[String] = configs.map(_.prefix).toSet

    def onlySetupConfigs(configs: Seq[SequenceConfig]): Seq[SetupConfig] = configs.collect { case ct: SetupConfig => ct }

    def onlyObserveConfigs(configs: Seq[SequenceConfig]): Seq[ObserveConfig] = configs.collect { case ct: ObserveConfig => ct }

    def onlyWaitConfigs(configs: Seq[SequenceConfig]): Seq[WaitConfig] = configs.collect { case ct: WaitConfig => ct }

    val prefixStartsWithFilter: String => ConfigFilter[ConfigKeyType] = query => sc => sc.prefix.startsWith(query)
    val prefixContainsFilter: String => ConfigFilter[ConfigKeyType] = query => sc => sc.prefix.contains(query)
    val prefixIsSubsystem: Subsystem => ConfigFilter[ConfigKeyType] = query => sc => sc.subsystem.equals(query)

    def prefixStartsWith(query: String, configs: Seq[ConfigKeyType]): Seq[ConfigKeyType] = configs.filter(prefixStartsWithFilter(query))

    def prefixContains(query: String, configs: Seq[ConfigKeyType]): Seq[ConfigKeyType] = configs.filter(prefixContainsFilter(query))

    def prefixIsSubsystem(query: Subsystem, configs: Seq[ConfigKeyType]): Seq[ConfigKeyType] = configs.filter(prefixIsSubsystem(query))
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
  @varargs
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
     * @param info    the implicit config info
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
     * @param info    the implicit config info
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
  final case class ConfigArgList(configs: Seq[SequenceConfigArg])

}
