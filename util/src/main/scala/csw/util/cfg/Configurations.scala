package csw.util.cfg

import java.io.{ByteArrayInputStream, ObjectInputStream, ObjectOutputStream, ByteArrayOutputStream}
import scala.language.implicitConversions

import csw.util.cfg.ConfigValues.ValueData

/**
 * Defines the different types of configurations: setup, observe, wait, ...
 */
object Configurations {

  import ConfigValues.CValue

  /**
   * Base trait for all Configuration Types
   */
  trait ConfigType {
    val obsId: String

    // Serializes this config (XXX TODO: Use Akka Protobuf serializer?)
    def toBinary: Array[Byte] = {
      val bos = new ByteArrayOutputStream
      val out = new ObjectOutputStream(bos)
      out.writeObject(this)
      out.close()
      bos.toByteArray
    }
  }

  object ConfigType {
    // Deserializes a ConfigType object from bytes
    def apply(bytes: Array[Byte]): ConfigType = {
      val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
      val obj = in.readObject()
      in.close()
      obj.asInstanceOf[ConfigType]
    }
  }

  type CV = CValue[_]

  /**
   * A setup config: used to set values before an observe.
   * @param obsId the observation id
   * @param prefix the prefix for the values (which only use simple names)
   * @param values collection of named values with optional units
   */
  case class SetupConfig(obsId: String, // obsId might be changed to some set of observation Info type
                         prefix: String = SetupConfig.DEFAULT_PREFIX,
                         values: Set[CV] = Set.empty[CV]) extends ConfigType {

    lazy val size = values.size

    lazy val names: Set[String] = values.map(c => c.name)

    /**
     * Returns a new instance including the given value
     */
    def :+[B <: CV](value: B): SetupConfig = {
      SetupConfig(obsId, prefix, values + value)
    }

    /**
     * Returns a new instance including the given values
     */
    def withValues(newValues: CV*): SetupConfig = {
      SetupConfig(obsId, prefix, values ++ newValues)
    }

    /**
     * Gets the ValueData associated with the given key
     * @param key the simple name for the value
     * @return Some[ValueData] if found, else None
     */
    def get(key: String): Option[ValueData[Any]] = {
      values.find(_.name == key).map(_.data)
    }

    /**
     * Gets the ValueData associated with the given key or throws an exception if not found
     * @param key the simple name for the value
     * @return the ValueData instance for the key
     */
    def apply(key: String): ValueData[Any] = get(key).get

    /**
     * Returns true if the config contains the given key
     */
    def exists(key: String): Boolean = values.exists(_.name == key)

    override def toString = "(" + obsId + ")->" + prefix + " " + values
  }

  object SetupConfig {
    val DEFAULT_PREFIX = ""

    def apply(obsId: String, prefix: String, values: CV*): SetupConfig = SetupConfig(obsId, prefix, values.toSet)

    // Create from serialized bytes
    def apply(bytes: Array[Byte]): SetupConfig = {
      ConfigType(bytes).asInstanceOf[SetupConfig]
    }
  }

  /**
   * Defines a wait config, used to pause the processing of configs (XXX TBD)
   */
  case class WaitConfig(obsId: String) extends ConfigType

  object WaitConfig {
    // Create from serialized bytes
    def apply(bytes: Array[Byte]): WaitConfig = {
      ConfigType(bytes).asInstanceOf[WaitConfig]
    }
  }

  /**
   * Defines an observe config, used to execute an observation (XXX TBD)
   */
  case class ObserveConfig(obsId: String) extends ConfigType

  object ObserveConfig {
    // Create from serialized bytes
    def apply(bytes: Array[Byte]): ObserveConfig = {
      ConfigType(bytes).asInstanceOf[ObserveConfig]
    }
  }

  /**
   * A list of configs (XXX should be a queue?)
   */
  type ConfigList = List[ConfigType]

  type SetupConfigList = List[SetupConfig]

  implicit def listToConfigList[A <: ConfigType](l: List[A]): ConfigListWrapper[A] = ConfigListWrapper(l)

  implicit def listToSetupConfigList[A <: SetupConfig](l: List[A]): SetupConfigListWrapper[A] = SetupConfigListWrapper(l)


  object ConfigList {

    // A filter type for various kinds of Configs
    type ConfigFilter[A] = A => Boolean

    // XXX may need to rethink this and access only as a queue, one at a time, since config list may include Wait and Observe configs
    val prefixStartsWithFilter: String => ConfigFilter[SetupConfig] = query => sc => sc.prefix.startsWith(query)
    val prefixContainsFilter: String => ConfigFilter[SetupConfig] = query => sc => sc.prefix.contains(query)

    def apply(configs: ConfigType*) = new ConfigListWrapper(configs.toList)
  }


  /**
   * Adds methods to a list of setup configs
   */
  case class SetupConfigListWrapper[A <: SetupConfig](configs: List[A]) {

    import ConfigList._

    def select(f: ConfigFilter[SetupConfig]): SetupConfigList = configs.filter(f)

  }

  /**
   * Adds methods to a list of configs
   */
  case class ConfigListWrapper[A <: ConfigType](configs: List[A]) {

    import ConfigList._

    lazy val prefixes: Set[String] = onlySetupConfigs.map(c => c.prefix).toSet

    lazy val obsIds: Set[String] = configs.map(sc => sc.obsId).toSet

    lazy val onlySetupConfigs: SetupConfigList = configs.collect { case sc: SetupConfig => sc}

    lazy val onlyWaitConfigs: List[WaitConfig] = configs.collect { case sc: WaitConfig => sc}

    lazy val onlyObserveConfigs: List[ObserveConfig] = configs.collect { case sc: ObserveConfig => sc}

    // XXX risk of skipping over WAIT and Observe configs?
    private def select(f: ConfigFilter[SetupConfig]): SetupConfigList = onlySetupConfigs.select(f)

    def prefixStartsWith(query: String): SetupConfigList = select(prefixStartsWithFilter(query))

    def prefixStartsWith(query: Option[String]): SetupConfigList =
      query match {
        case None => onlySetupConfigs
        case Some(q) => select(prefixStartsWithFilter(q))
      }

    def prefixContains(query: String): SetupConfigList = select(prefixContainsFilter(query))

    lazy val getFirst: SetupConfigList = {
      val scList = onlySetupConfigs
      scList.select(prefixStartsWithFilter(scList.head.prefix))
    }
  }

}


