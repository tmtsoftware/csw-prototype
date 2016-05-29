package csw.util.config3

import java.util.Optional

import csw.util.config3.UnitsOfMeasure.Units

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

  type ConfigData = Set[Item[_]]

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
    def add[A](item: Item[A]): T = {
      val configRemoved: T = removeByKeyname(item.keyName)
      create(configRemoved.items + item)
    }

    def set[A](key: Key[A], v: Vector[A], units: Units): T = {
      val newItem = key.set(v, units)
      add(newItem)
    }

    /**
      * Lookup a Key in Map and returns an Option
      *
      * @param key the Key to be used for lookup
      * @return an option value typed to the Key
      */
    def get[A](key: Key[A]): Option[Item[A]] = getByKeyname[A](key.keyName)

    def jget[A](key: Key[A]): Optional[Item[A]] = get(key).asJava

    def jget[A](key: Key[A], index: Int): A = get(key).get.value(index)

    def exists[A](key: Key[A]): Boolean = get(key).isDefined

    /**
      * Remove a Key from the Map and return a new Map
      *
      * @param key the Key to be used for removal
      * @return a new T, where T is a ConfigType child with the key removed or identical if the key is not present
      */
    def remove[A](key: Key[A]): T = removeByKeyname(key.keyName)

    private def removeByKeyname(keyname: String): T = {
      val f = getByKeyname(keyname)
      f match {
        case Some(item) => create(items.-(item))
        case None => this
      }
    }

    private def getByKeyname[A](keyname: String): Option[Item[A]] =
      items.find(_.keyName == keyname).asInstanceOf[Option[Item[A]]]

    /**
      * Return the value associated with a Key rather than an Option
      *
      * @param key the Key to be used for lookup
      * @return the item associated with the Key or a NoSuchElementException if the key does not exist
      */
    final def apply[A](key: Key[A]): Seq[A] = get[A](key).get.value

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

  case class SetupConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]]) extends ConfigType[SetupConfig] {
    override def create(data: ConfigData) = SetupConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following three seem to be needed by Java since Java can't handle the return type of ConfigType add/set
    override def add[A](item: Item[A]): SetupConfig = super.add(item)

    override def set[A](key: Key[A], v: Vector[A], units: Units): SetupConfig = super.set[A](key, v, units)

    override def remove[A](key: Key[A]): SetupConfig = super.remove[A](key)

    override def toString = doToString("SC")
  }

  case class ObserveConfig(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]]) extends ConfigType[ObserveConfig] {
    override def create(data: ConfigData) = ObserveConfig(configKey, data)

    // This is here for Java to construct with String
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    // The following three seem to be needed by Java since Java can't handle the return type of ConfigType add/set
    override def add[A](item: Item[A]): ObserveConfig = super.add(item)

    override def set[A](key: Key[A], v: Vector[A], units: Units): ObserveConfig = super.set[A](key, v, units)

    override def remove[A](key: Key[A]): ObserveConfig = super.remove[A](key)

    override def toString = doToString("OC")
  }

}
