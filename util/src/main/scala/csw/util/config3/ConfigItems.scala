package csw.util.config3

import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}

import scala.annotation.varargs
import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
  * The type of a configuration item
  * @tparam S the Scala type
  * @tparam J the Java type (may be the same)
  */
trait Item[+S, +J] {
  /**
    * @return the name of the key for this item
    */
  def keyName: String

  /**
    * @return The values for this item
    */
  def value: Vector[S]

  /**
    * Internal Java API (Needed for the implementation: Java callers should use jvalues() instead)
    *
    * @return the values as a Scala Vector of Java values
    */
  def jvalue: Vector[J]

  /**
    * @return the units for the values
    */
  def units: Units

  /**
    * @param index the index of a value
    * @return the value at the given index (may throw an exception if the index is out of range)
    */
  def apply(index: Int): S = value(index)

  /**
    * Java API to get the value at the given index
    * @param index the index of a value
    * @return the value at the given index (may throw an exception if the index is out of range)
    */
  def jget(index: Int): J = jvalue(index)

  /**
    * Sets the units for the values
    * @param units the units for the values
    * @return a new instance of this item with the units set
    */
  def withUnits(units: Units): Item[S, J]
}

abstract class Key[S, J](val keyName: String) extends Serializable {

  override def toString = keyName

  override def equals(that: Any): Boolean = {
    that match {
      case that: Key[S, J] ⇒ this.keyName == that.keyName
      case _            ⇒ false
    }
  }

  override def hashCode: Int = 41 * keyName.hashCode

  def set(v: Vector[S], units: Units): Item[S, J]

  def set(v: S*): Item[S, J]

  def jset(v: Vector[J], units: Units): Item[S, J]

  @varargs def jset(v: J*): Item[S, J]
}

