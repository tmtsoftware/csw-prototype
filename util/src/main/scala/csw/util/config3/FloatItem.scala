package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an FloatKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class FloatItem(keyName: String, values: Vector[Float], units: Units) extends Item[Float, java.lang.Float] {

  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.Float] = values.map(i ⇒ i: java.lang.Float).asJava

  override def jvalue(index: Int): java.lang.Float = values(index)

  /**
   * Java API to get the value at the given index
   *
   * @param index the index of a value
   * @return Some value at the given index, if the index is in range, otherwise None
   */
  def jget(index: Int): java.util.Optional[java.lang.Float] = get(index).map(i ⇒ i: java.lang.Float).asJava

  /**
   * Java API to get the first or default value
   * @return the first or default value (Use this if you know there is only a single value)
   */
  def jvalue: java.lang.Float = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Float values
 *
 * @param nameIn the name of the key
 */
final case class FloatKey(nameIn: String) extends Key[Float, java.lang.Float](nameIn) {

  override def set(v: Vector[Float], units: Units = NoUnits) = FloatItem(keyName, v, units)

  override def set(v: Float*) = FloatItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Float]): FloatItem = FloatItem(keyName, v.asScala.toVector.map(i ⇒ i: Float), NoUnits)

  @varargs
  override def jset(v: java.lang.Float*) = FloatItem(keyName, v.map(i ⇒ i: Float).toVector, units = UnitsOfMeasure.NoUnits)
}

