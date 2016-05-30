package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.Units

/**
 * The type of a value for an ShortKey
 *
 * @param keyName the name of the key
 * @param value   the value for the key
 * @param units   the units of the value
 */
final case class ShortItem(keyName: String, value: Vector[Short], units: Units) extends Item[Short, java.lang.Short] {
  //  /**
  //    * Java API
  //    *
  //    * @return the values as a Scala Vector
  //    */
  //  override def jvalue: Vector[java.lang.Short] = value.map(i ⇒ i: java.lang.Short)

  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.Short] = value.map(i ⇒ i: java.lang.Short).asJava

  /**
   * Java API
   *
   * @return the value at the given index
   */
  override def jget(index: Int): java.lang.Short = value(index)

  /**
   * Set the units of the value
   *
   * @param unitsIn the units to set
   * @return a copy of this item with the given units set
   */
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Short values
 *
 * @param nameIn the name of the key
 */
final case class ShortKey(nameIn: String) extends Key[Short, java.lang.Short](nameIn) {

  //  /**
  //    * Sets the values for the key
  //    *
  //    * @param v     the values
  //    * @param units the units of the values
  //    * @return a new item containing the key name, values and units
  //    */
  //  override def set(v: Vector[Short], units: Units) = ShortItem(keyName, v, units)

  /**
   * Sets the values for the key using a variable number of arguments
   *
   * @param v the values
   * @return a new item containing the key name, values and no units
   */
  override def set(v: Short*) = ShortItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  //  /**
  //    * Java API to set the values for a key
  //    *
  //    * @param v     the values as a java list
  //    * @param units the units of the values
  //    * @return a new item containing the key name, values and units
  //    */
  //  def jset(v: Vector[java.lang.Short], units: Units) = ShortItem(keyName, v.map(i ⇒ i: Short), units)
  //
  //  /**
  //    * Java API to set the values for a key
  //    *
  //    * @param v     the values as a java list
  //    * @param units the units of the values
  //    * @return a new item containing the key name, values and units
  //    */
  //  def jset(v: java.util.List[java.lang.Short], units: Units) = jset(v.asScala.toVector, units)

  /**
   * Java API: Sets the values for the key using a variable number of arguments
   *
   * @param v the values
   * @return a new item containing the key name, values and no units
   */
  @varargs
  override def jset(v: java.lang.Short*) = ShortItem(keyName, v.map(i ⇒ i: Short).toVector, units = UnitsOfMeasure.NoUnits)
}

