package csw.util.config

import java.util.Optional

import csw.util.config.UnitsOfMeasure.{ NoUnits, Units }

import scala.compat.java8.OptionConverters._
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * The type of an item contained in a configuration
 *
 * @tparam A the value type
 */
trait Item[A] {
  /**
   * The key for this item
   */
  def key: Key1[A]

  /**
   * The value for this item
   */
  def value: A

  /**
   * The units of the value
   */
  def units: Units
}

/**
 * The shared class for storing telemetry and configuration data.
 *
 * @param nameIn the name of the key
 */
abstract class Key1[T](nameIn: String, unitsIn: Units = NoUnits) extends Serializable {

  def name: String = nameIn

  def units: Units = unitsIn

  override def toString = nameIn + unitsIn

  override def equals(that: Any): Boolean = {
    that match {
      case that: Key1[T] => this.units == that.units && this.name == that.name
      case _ => false
    }
  }

  override def hashCode: Int = 41 * name.hashCode * (41 * units.hashCode)

  def set(v: T): CItem[T]
}

case class CItem[A](key: Key1[A], units: Units, value: A) extends Item[A] {
  override def toString = key + "(" + value.toString + ")"
}

case class IntKey(nameIn: String, unitsIn: Units) extends Key1[Integer](nameIn, unitsIn) {
  def set(v: Integer) = CItem[Integer](this, units, v)
}

case class StringKey(nameIn: String, unitsIn: Units) extends Key1[String](nameIn, unitsIn) {
  def set(v: String) = CItem[String](this, units, v)
}

case class DoubleKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Double](nameIn, unitsIn) {
  def set(v: java.lang.Double) = CItem[java.lang.Double](this, units, v)
}

case class JKey1[A](nameIn: String, unitsIn: Units = NoUnits) extends Key1[A](nameIn, unitsIn) {
  def set(v: A) = CItem[A](this, units, v)
}

/**
 * Key for an array of values of type A in the given units.
 */
case class ArrayKey[A](nameIn: String, unitsIn: Units) extends Key1[Seq[A]](nameIn, unitsIn) {
  def set(v: Seq[A]) = CItem[Seq[A]](this, units, v)

  /**
   * Allows setting the value from Scala with a variable number of arguments
   */
  def set[X: ClassTag](v: A*) = CItem(this, units, v.toSeq)

  /**
   * Java varargs API: allows setting one or more values from Java
   */
  @annotation.varargs
  def jset(v: A*) = CItem(this, units, v.toSeq)
}

///**
//  * A key that has an int array as a value
//  */
//class IntArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[Int](nameIn, unitsIn)
case class IntArrayKey(nameIn: String, unitsIn: Units) extends Key1[Seq[java.lang.Integer]](nameIn, unitsIn) {
  def set(v: Seq[java.lang.Integer]) = CItem[Seq[java.lang.Integer]](this, units, v)

  /**
   * Allows setting the value from Scala with a variable number of arguments
   */
  def set[X: ClassTag](v: java.lang.Integer*) = CItem(this, units, v.toSeq)

  /**
   * Java varargs API: allows setting one or more values from Java
   */
  @annotation.varargs
  def jset(v: java.lang.Integer*) = CItem(this, units, v.toSeq)
}

/**
 * A key that has a double array as a value
 */
//class DoubleArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[Double](nameIn, unitsIn)
case class DoubleArrayKey(nameIn: String, unitsIn: Units) extends Key1[Seq[java.lang.Double]](nameIn, unitsIn) {
  def set(v: Seq[java.lang.Double]) = CItem[Seq[java.lang.Double]](this, units, v)

  /**
   * Allows setting the value from Scala with a variable number of arguments
   */
  def set[X: ClassTag](v: java.lang.Double*) = CItem(this, units, v.toSeq)

  /**
   * Java varargs API: allows setting one or more values from Java
   */
  @annotation.varargs
  def jset(v: java.lang.Double*) = CItem(this, units, v.toSeq)
}

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
     * Holds the typed key/value pairs
     */
    protected def data: ConfigData

    /**
     * The number of key/value pairs in the config
     */
    def size = data.size

    /**
     * Returns a new instance with the value for the given key set to the given value
     *
     * @param item the key, which also contains the value type
     * @return a new instance of this object with the key set to the given value
     */
    def add(item: Item[_]): T

    /**
     * Lookups a Key in the config and returns an Option
     *
     * @param key the Key to be used for lookup
     * @return an option value typed to the Key
     */
    def get[A](key: Key1[A]): Option[Item[A]] = data.find(_.key.name == key.name).asInstanceOf[Option[Item[A]]]

    /**
     * For Java API: Looks up a Key in the config and returns an Optional
     *
     * @param key the Key to be used for lookup
     * @return an optional value typed to the Key
     */
    def jget[A](key: Key1[A]): Optional[Item[A]] = get(key).asJava

    def exists[A](key: Key1[A]): Boolean = get(key).isDefined

    /**
     * Removes a Key from the config and returns a new config
     *
     * @param key the Key to be used for removal
     * @return a new T, where T is a ConfigType child
     */
    def remove[A](key: Key1[A]): ConfigType[T]

    /**
     * Return the value associated with a Key rather than an Option
     *
     * @param key the Key to be used for lookup
     * @return the value associated with the Key or a NoSuchElementException if the key does not exist
     */
    final def apply[A](key: Key1[A]) = get[A](key).getOrElse(None)

    /**
     * Returns the set of keys in the config
     */
    final def getKeys: Set[Key1[_]] = data.map(_.key)

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

    protected def dataToString = data.mkString("(", ",", ")")

    protected def doToString(kind: String) = s"$kind[$subsystem, $prefix]$dataToString"
  }

  case class SetupConfig(configKey: ConfigKey, data: ConfigData = Set.empty[Item[_]]) extends ConfigType[SetupConfig] {
    override def create(data: ConfigData) = SetupConfig(configKey, data)

    /**
      * Returns a new SetupConfig with the contents of this SetupConfig and with the given item added (or replaced)
      */
    override def add(ci: Item[_]): SetupConfig = {
      val sc = remove(ci.key)
      SetupConfig(configKey, sc.data + ci)
    }

    /**
      * Returns a new SetupConfig with the contents of this SetupConfig and with the given key set to the given value
      */
    def set[T](key: Key1[T], value: T): SetupConfig = {
      val sc = remove(key)
      create(sc.data + key.set(value))
    }

    override def remove[T](key: Key1[T]): SetupConfig = {
      val f = get(key)
      f match {
        case Some(item) => SetupConfig(configKey, data - item)
        case None => this
      }
    }

    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    override def toString = doToString("SC")
  }

}
