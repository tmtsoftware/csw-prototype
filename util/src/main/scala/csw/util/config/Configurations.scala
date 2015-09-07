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

    // Returned if no prefix was found
    val DEFAULT_PREFIX = "BAD.prefix"

    /**
     * Creates a ConfigKey from the given string
     */
    implicit def apply(in: String): ConfigKey = ConfigKey(subsystem(in), prefix(in))

    // Returns the prefix of the given string (up to last SEPARATOR), or DEFAULT_PREFIX, if no SEPARATOR was found
    private def prefix(keyText: String): String = keyText.splitAt(keyText.lastIndexOf(SEPARATOR))._1 match {
      case s if s.nonEmpty ⇒ s
      case _ ⇒ DEFAULT_PREFIX
    }

    // Returns the subsystem for the given string (up to first SEPARATOR), or Subsystem.BAD, if no SEPARATOR was found
    private def subsystem(keyText: String): Subsystem =
      Subsystem.lookup(keyText.splitAt(keyText.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
  }

  /**
   * Convenience class for getting the subsystem and prefix from a config key string
   */
  case class ConfigKey(subsystem: Subsystem, prefix: String) {
    override def toString = "[" + subsystem + ", " + prefix + "]"
  }


  sealed trait ConfigType {
    def configKey: ConfigKey

    def subsystem: Subsystem = configKey.subsystem

    def prefix: String = configKey.prefix

    def data: ConfigData

    def size = data.size

    def get(key: Key): Option[key.Value] = data.get(key)

    def doToString(kind: String) =
      kind + "[" + subsystem + ", " + prefix + "] " + data.toString
  }

  trait SequenceConfig

  trait ControlConfig

  case class SetupConfig(configKey: ConfigKey, data: ConfigData = ConfigData())
    extends ConfigType with SequenceConfig with ControlConfig {

    def set(key: Key)(value: key.Value): SetupConfig = SetupConfig(configKey, data.set(key)(value))

    def remove(key: Key): SetupConfig = SetupConfig(configKey, data.remove(key))

    override def toString = doToString("SC")
  }

  case class ObserveConfig(configKey: ConfigKey, data: ConfigData = ConfigData())
    extends ConfigType with SequenceConfig with ControlConfig {
    def set(key: Key)(value: key.Value): ObserveConfig = ObserveConfig(configKey, data.set(key)(value))

    def remove(key: Key): ObserveConfig = ObserveConfig(configKey, data.remove(key))

    override def toString = doToString("OC")
  }

  case class WaitConfig(configKey: ConfigKey, data: ConfigData = ConfigData())
    extends ConfigType with SequenceConfig {
    def set(key: Key)(value: key.Value): WaitConfig = WaitConfig(configKey, data.set(key)(value))

    def remove(key: Key): WaitConfig = WaitConfig(configKey, data.remove(key))

    override def toString = doToString("WAIT")
  }

  /**
   * Filters for
   */
  object ConfigFilters {
    // A filter type for various ConfigData
    type ConfigFilter[A] = A ⇒ Boolean

    def prefixes(configs: Seq[ConfigType]): Set[String] = configs.map(_.prefix).toSet

    def onlySetupConfigs(configs: Seq[ConfigType]): Seq[SetupConfig] = configs.collect { case ct: SetupConfig ⇒ ct }

    def onlyObserveConfigs(configs: Seq[ConfigType]): Seq[ObserveConfig] = configs.collect { case ct: ObserveConfig ⇒ ct }

    def onlyWaitConfigs(configs: Seq[ConfigType]): Seq[WaitConfig] = configs.collect { case ct: WaitConfig ⇒ ct }

    val prefixStartsWithFilter: String ⇒ ConfigFilter[ConfigType] = query ⇒ sc ⇒ sc.prefix.startsWith(query)
    val prefixContainsFilter: String ⇒ ConfigFilter[ConfigType] = query ⇒ sc ⇒ sc.prefix.contains(query)

    def prefixStartsWith(query: String, configs: Seq[ConfigType]): Seq[ConfigType] = configs.filter(prefixStartsWithFilter(query))

    def prefixContains(query: String, configs: Seq[ConfigType]): Seq[ConfigType] = configs.filter(prefixContainsFilter(query))
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
   * A ConfigArg is what is placed in a queue in Command Service.
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

  final class WaitConfigArg(val info: ConfigInfo, val config: WaitConfig) extends ConfigArg

  object WaitConfigArg {
    def apply(configInfo: ConfigInfo, wc: WaitConfig) = new WaitConfigArg(configInfo, wc)
  }

  type ConfigArgList = Seq[SequenceConfig]

  // For getting device configuration
  final case class ConfigQuery(configs: Seq[SetupConfig])

  sealed trait ConfigQueryResponse

  final case class QuerySuccess(configs: Seq[SetupConfig]) extends ConfigQueryResponse

  final case class QueryFailure(reason: String) extends ConfigQueryResponse

}
