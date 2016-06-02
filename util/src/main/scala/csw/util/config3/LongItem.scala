package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an LongKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class LongItem(keyName: String, values: Vector[Long], units: Units) extends Item[Long, java.lang.Long] {
  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.Long] = values.map(i ⇒ i: java.lang.Long).asJava

  override def jvalue(index: Int): java.lang.Long = values(index)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)

  /**
   * Java API to get the value at the given index
   *
   * @param index the index of a value
   * @return Some value at the given index, if the index is in range, otherwise None
   */
  def jget(index: Int): java.util.Optional[java.lang.Long] = get(index).map(i ⇒ i: java.lang.Long).asJava

  /**
   * Java API to get the first or default value
   * @return the first or default value (Use this if you know there is only a single value)
   */
  def jvalue: java.lang.Long = values(0)
}

/**
 * A key of Long values
 *
 * @param nameIn the name of the key
 */
final case class LongKey(nameIn: String) extends Key[Long, java.lang.Long](nameIn) {

  override def set(v: Vector[Long], units: Units = NoUnits) = LongItem(keyName, v, units)

  override def set(v: Long*) = LongItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Long]): LongItem = LongItem(keyName, v.asScala.toVector.map(i ⇒ i: Long), NoUnits)

  @varargs
  override def jset(v: java.lang.Long*) = LongItem(keyName, v.map(i ⇒ i: Long).toVector, units = UnitsOfMeasure.NoUnits)
}

