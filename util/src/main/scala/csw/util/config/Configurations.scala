package csw.util.config

import scala.language.implicitConversions
import csw.util.config.Subsystem.Subsystem

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Configurations {

  /**
   * Contains implicit def for getting the subsystem and prefix from a config key string
   */
  object ConfigKey {
    // Path separator
    val SEPARATOR = '.'

    /**
     * Creates a ConfigKey from the given string
     */
    implicit def apply(in: String): ConfigKey = ConfigKey(subsystem(in), in)

    // Returns the subsystem for the given string (up to first SEPARATOR), or Subsystem.BAD, if no SEPARATOR was found
    private def subsystem(keyText: String): Subsystem =
      Subsystem.lookup(keyText.splitAt(keyText.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
  }

  /**
   * Convenience class for getting the subsystem and prefix from a config key string
   */
  case class ConfigKey(subsystem: Subsystem, path: String) {
    override def toString = "[" + subsystem + ", " + path + "]"
  }


  /**
   * Base trait for configurations: Defines the subsystem, prefix and a method to get the value for a key.
   * The config key is based on a string like subsystem.x.y.z, where the prefix is then subsystem.x.y.
   */
  sealed trait ConfigType {
    def configKey: ConfigKey

    /**
     * The subsystem for the config
     */
    def subsystem: Subsystem = configKey.subsystem

    /**
     * The prefix for the config
     */
    def path: String = configKey.path

    /**
     * Holds the typed key/value pairs
     */
    def data: ConfigData

    /**
     * The number of key/value pairs
     */
    def size = data.size

    /**
     * Returns the value for the key, if found
     */
    def get(key: Key): Option[key.Value] = data.get(key)

    def doToString(kind: String) =
      kind + "[" + subsystem + ", " + path + "] " + data.toString
  }

  /**
   * Marker trait for sequence configurations
   */
  trait SequenceConfig

  /**
   * Marker trait for control configurations
   */
  trait ControlConfig

  /**
   * Defines a setup configuration, which is a config key plus a set of key/value pairs
   * @param configKey the key for the configuration, containing subsystem and prefix
   * @param data the typed key/value pairs
   */
  case class SetupConfig(configKey: ConfigKey, data: ConfigData = ConfigData())
    extends ConfigType with SequenceConfig with ControlConfig {

    def set(key: Key)(value: key.Value): SetupConfig = SetupConfig(configKey, data.set(key)(value))

    def remove(key: Key): SetupConfig = SetupConfig(configKey, data.remove(key))

    override def toString = doToString("SC")
  }

  /**
   * Defines an observe configuration, which is a config key plus a set of key/value pairs
   * @param configKey the key for the configuration, containing subsystem and prefix
   * @param data the typed key/value pairs
   */
  case class ObserveConfig(configKey: ConfigKey, data: ConfigData = ConfigData())
    extends ConfigType with SequenceConfig with ControlConfig {
    def set(key: Key)(value: key.Value): ObserveConfig = ObserveConfig(configKey, data.set(key)(value))

    def remove(key: Key): ObserveConfig = ObserveConfig(configKey, data.remove(key))

    override def toString = doToString("OC")
  }

  /**
   * Defines a wait configuration
   * @param configKey the key for the configuration, containing subsystem and prefix
   * @param data the typed key/value pairs
   */
  case class WaitConfig(configKey: ConfigKey, data: ConfigData = ConfigData())
    extends ConfigType with SequenceConfig {
    def set(key: Key)(value: key.Value): WaitConfig = WaitConfig(configKey, data.set(key)(value))

    def remove(key: Key): WaitConfig = WaitConfig(configKey, data.remove(key))

    override def toString = doToString("WAIT")
  }

  /**
   * Filters for sequences of configs
   */
  object ConfigFilters {
    // A filter type for various ConfigData
    type ConfigFilter[A] = A ⇒ Boolean

    def paths(configs: Seq[ConfigType]): Set[String] = configs.map(_.path).toSet

    def onlySetupConfigs(configs: Seq[ConfigType]): Seq[SetupConfig] = configs.collect { case ct: SetupConfig ⇒ ct }

    def onlyObserveConfigs(configs: Seq[ConfigType]): Seq[ObserveConfig] = configs.collect { case ct: ObserveConfig ⇒ ct }

    def onlyWaitConfigs(configs: Seq[ConfigType]): Seq[WaitConfig] = configs.collect { case ct: WaitConfig ⇒ ct }

    private val pathStartsWithFilter: String ⇒ ConfigFilter[ConfigType] = query ⇒ sc ⇒ sc.path.startsWith(query)
    private val pathContainsFilter: String ⇒ ConfigFilter[ConfigType] = query ⇒ sc ⇒ sc.path.contains(query)

    def pathStartsWith(query: String, configs: Seq[ConfigType]): Seq[ConfigType] = configs.filter(pathStartsWithFilter(query))

    def pathContains(query: String, configs: Seq[ConfigType]): Seq[ConfigType] = configs.filter(pathContainsFilter(query))
  }

  /**
   * This will include information related to the observation that is related to a configuration.
   * This will grow and develop.
   */
  case class ConfigInfo(obsId: ObsID)

  object ConfigInfo {
    implicit def apply(obsId: String): ConfigInfo = ConfigInfo(ObsID(obsId))
  }

  /**
   * A ConfigArg is what is placed in a Submit message in the Command Service queue.
   * It can be one or more SetupConfigs, one or more ObserveConfigs or a WaitConfig
   * Each ConfigArg includes a ConfigInfo which will contain information about the executing
   * observation.
   */
  sealed trait ConfigArg


  final case class SetupConfigArg(info: ConfigInfo, configs: Seq[SetupConfig]) extends ConfigArg

  object SetupConfigArg {
    def apply(configs: SetupConfig*)(implicit info: ConfigInfo): SetupConfigArg = SetupConfigArg(info, configs.toSeq)
  }


  final case class ObserveConfigArg(info: ConfigInfo, configs: Seq[ObserveConfig]) extends ConfigArg

  object ObserveConfigArg {
    def apply(configs: ObserveConfig*)(implicit info: ConfigInfo): ObserveConfigArg = ObserveConfigArg(info, configs.toSeq)
  }


  final case class WaitConfigArg(info: ConfigInfo, config: WaitConfig) extends ConfigArg

  object WaitConfigArg {
    def apply(config: WaitConfig)(implicit info: ConfigInfo): WaitConfigArg = WaitConfigArg(info, config)
  }


  type ConfigArgList = Seq[SequenceConfig]

  // For getting device configuration
  final case class ConfigQuery(configs: Seq[SetupConfig])

  sealed trait ConfigQueryResponse

  final case class QuerySuccess(configs: Seq[SetupConfig]) extends ConfigQueryResponse

  final case class QueryFailure(reason: String) extends ConfigQueryResponse

}
