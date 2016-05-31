package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.Units

/**
 * The type of a value for an DoubleKey
 *
 * @param keyName the name of the key
 * @param value   the value for the key
 * @param units   the units of the value
 */
final case class DoubleItem(keyName: String, value: Vector[Double], units: Units) extends Item[Double, java.lang.Double] {
  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.Double] = value.map(i ⇒ i: java.lang.Double).asJava

  /**
   * Java API
   *
   * @return the value at the given index
   */
  override def jget(index: Int): java.lang.Double = value(index)

  /**
   * Set the units of the value
   *
   * @param unitsIn the units to set
   * @return a copy of this item with the given units set
   */
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Double values
 *
 * @param nameIn the name of the key
 */
final case class DoubleKey(nameIn: String) extends Key[Double, java.lang.Double](nameIn) {

  /**
   * Sets the values for the key using a variable number of arguments
   *
   * @param v the values
   * @return a new item containing the key name, values and no units
   */
  override def set(v: Double*) = DoubleItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  /**
   * Java API: Sets the values for the key using a variable number of arguments
   *
   * @param v the values
   * @return a new item containing the key name, values and no units
   */
  @varargs
  override def jset(v: java.lang.Double*) = DoubleItem(keyName, v.map(i ⇒ i: Double).toVector, units = UnitsOfMeasure.NoUnits)
}

