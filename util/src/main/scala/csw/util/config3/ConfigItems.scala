package csw.util.config3

import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * The type of a configuration item
 *
 * @tparam S the Scala type
 * @tparam J the Java type (may be the same)
 */
trait Item[S, J] {
  /**
   * @return the name of the key for this item
   */
  def keyName: String

  /**
   * @return The values for this item
   */
  def values: Vector[S]

  /**
   * The number of values in this item (values.size)
   *
   * @return
   */
  def size: Int = values.size

  /**
   * @return the units for the values
   */
  def units: Units

  /**
   * Returns the value at the given index, throwing an exception if the index is out of range
   * @param index the index of a value
   * @return the value at the given index (may throw an exception if the index is out of range)
   */
  def apply(index: Int): S = values(index)

  /**
   * Returns the value at the given index, throwing an exception if the index is out of range
   *
   * @param index the index of a value
   * @return the value at the given index (may throw an exception if the index is out of range)
   */
  def value(index: Int): S = values(index)

  /**
   * @param index the index of a value
   * @return Some value at the given index, if the index is in range, otherwise None
   */
  def get(index: Int): Option[S] = values.lift(index)

  /**
   * @return the first or default value (Use this if you know there is only a single value)
   */
  def value: S = values(0)

  /**
   * Java API to get the value at the given index
   *
   * @param index the index of a value
   * @return the value at the given index (may throw an exception if the index is out of range)
   */
  def jvalue(index: Int): J

  /**
   * Java API to get the value at the given index
   *
   * @param index the index of a value
   * @return Some value at the given index, if the index is in range, otherwise None
   */
  def jget(index: Int): java.util.Optional[J]

  /**
   * Java API to get the first or default value
   *
   * @return the first or default value (Use this if you know there is only a single value)
   */
  def jvalue: J

  /**
   * Java API: Returns the item's values as a java list
   * @return the list of values
   */
  def jvalues: java.util.List[J]

  /**
   * Sets the units for the values
   *
   * @param units the units for the values
   * @return a new instance of this item with the units set
   */
  def withUnits(units: Units): Item[S, J]
}

/**
 * The type of a configuration item key.
 *
 * @param keyName the key
 * @tparam S the value's Scala type
 * @tparam J the value's Java type (will be converted to/from Scala, may be the same)
 */
abstract class Key[S, J](val keyName: String) extends Serializable {

  /**
   * Sets the values for the key as a Scala Vector
   *
   * @param v     a vector of values
   * @param units optional units of the values (defaults to no units)
   * @return an item containing the key name, values and units
   */
  def set(v: Vector[S], units: Units = NoUnits): Item[S, J]

  /**
   * Sets the values for the key using a variable number of arguments
   *
   * @param v one or more values
   * @return an item containing the key name, values (call withUnits() on the result to set the units)
   */
  def set(v: S*): Item[S, J]

  /**
   * Java API: Sets the values for the key as a Java list
   *
   * @param v a list of values
   * @return an item containing the key name, values (call withUnits() on the result to set the units)
   */
  def jset(v: java.util.List[J]): Item[S, J]

  /**
   * Java API: Sets the values for the key using a variable number of arguments
   *
   * @param v one or more values
   * @return an item containing the key name, values (call withUnits() on the result to set the units)
   */
  def jset(v: J*): Item[S, J]

  override def toString = keyName

  override def equals(that: Any): Boolean = {
    that match {
      case that: Key[S, J] ⇒ this.keyName == that.keyName
      case _               ⇒ false
    }
  }

  override def hashCode: Int = 41 * keyName.hashCode
}

