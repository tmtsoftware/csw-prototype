package csw.util.config3

import java.util.Optional

import csw.util.config3.UnitsOfMeasure.Units

import scala.annotation.varargs
import scala.compat.java8.OptionConverters._
import scala.language.implicitConversions

/**
 * TMT Source Code: 5/22/16.
 */
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

  type ConfigData = Set[Item[_, _]]

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
     * Holds the Set of Items
     */
    protected def items: ConfigData

    /**
     * The number of items in the configurations
     *
     * @return the number of items in the configuration
     */
    def size = items.size

    /**
     * Returns a new instance with the value for the given key set to the given value
     *
     * @param item the key, which also contains the value type
     * @return a new instance of this object with the key set to the given value
     */
    def add[S, J](item: Item[S, J]): T = {
      val configRemoved: T = removeByKeyname(item.keyName)
      create(configRemoved.items + item)
    }

    def set[S, J](key: Key[S, J], units: Units, v: S*): T = {
      val newItem = key.set(v: _*).withUnits(units)
      add(newItem)
    }

    def jset[S, J](key: Key[S, J], units: Units, v: S*): T = set(key, units, v: _*)

    /**
     * Lookup a Key in Map and returns an Option
     *
     * @param key the Key to be used for lookup
     * @return an option value typed to the Key
     */
    def get[S, J](key: Key[S, J]): Option[Item[S, J]] = getByKeyname[S, J](key.keyName)

    def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = get(key).asJava

    def jget[S, J](key: Key[S, J], index: Int): J = get(key).get.jget(index)

    def exists[S, J](key: Key[S, J]): Boolean = get(key).isDefined

    /**
     * Remove a Key from the Map and return a new Map
     *
     * @param key the Key to be used for removal
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
     * @return the item associated with the Key or a NoSuchElementException if the key does not exist
     */
    final def apply[S, J](key: Key[S, J]): Seq[S] = get[S, J](key).get.value

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
  }

  case class SetupConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]]) extends ConfigType[SetupConfig] {
    override def create(data: ConfigData) = SetupConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following three seem to be needed by Java since Java can't handle the return type of ConfigType add/set
    override def add[S, J](item: Item[S, J]): SetupConfig = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): SetupConfig = super.set[S, J](key, units, v: _*)

    @varargs override def jset[S, J](key: Key[S, J], units: Units, v: S*): SetupConfig = super.jset(key, units, v: _*)

    override def remove[S, J](key: Key[S, J]): SetupConfig = super.remove[S, J](key)

    override def toString = doToString("SC")
  }

  case class ObserveConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]]) extends ConfigType[ObserveConfig] {
    override def create(data: ConfigData) = ObserveConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following seem to be needed by Java since Java can't handle the return type of ConfigType add/set
    override def add[S, J](item: Item[S, J]): ObserveConfig = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): ObserveConfig = super.set[S, J](key, units, v: _*)

    @varargs override def jset[S, J](key: Key[S, J], units: Units, v: S*): ObserveConfig = super.jset(key, units, v: _*)

    override def remove[S, J](key: Key[S, J]): ObserveConfig = super.remove[S, J](key)

    override def toString = doToString("OC")
  }

}
