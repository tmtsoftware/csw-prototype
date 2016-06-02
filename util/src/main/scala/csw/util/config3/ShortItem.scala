package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an ShortKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class ShortItem(keyName: String, values: Vector[Short], units: Units) extends Item[Short, java.lang.Short] {

  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.Short] = values.map(i ⇒ i: java.lang.Short).asJava

  override def jvalue(index: Int): java.lang.Short = values(index)

  /**
   * Java API to get the value at the given index
   *
   * @param index the index of a value
   * @return Some value at the given index, if the index is in range, otherwise None
   */
  def jget(index: Int): java.util.Optional[java.lang.Short] = get(index).map(i ⇒ i: java.lang.Short).asJava

  /**
   * Java API to get the first or default value
   * @return the first or default value (Use this if you know there is only a single value)
   */
  def jvalue: java.lang.Short = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Short values
 *
 * @param nameIn the name of the key
 */
final case class ShortKey(nameIn: String) extends Key[Short, java.lang.Short](nameIn) {

  override def set(v: Vector[Short], units: Units = NoUnits) = ShortItem(keyName, v, units)

  override def set(v: Short*) = ShortItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Short]): ShortItem = ShortItem(keyName, v.asScala.toVector.map(i ⇒ i: Short), NoUnits)

  @varargs
  override def jset(v: java.lang.Short*) = ShortItem(keyName, v.map(i ⇒ i: Short).toVector, units = UnitsOfMeasure.NoUnits)
}

