package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}

/**
 * The type of a value for an IntKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class IntItem(keyName: String, values: Vector[Int], units: Units) extends Item[Int, java.lang.Integer] {
  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.Integer] = values.map(i ⇒ i: java.lang.Integer).asJava

  override def jget(index: Int): java.lang.Integer = values(index)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Int values
 *
 * @param nameIn the name of the key
 */
final case class IntKey(nameIn: String) extends Key[Int, java.lang.Integer](nameIn) {

  override def set(v: Vector[Int], units: Units = NoUnits) = IntItem(keyName, v, units)

  override def set(v: Int*) = IntItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Integer]): IntItem = IntItem(keyName, v.asScala.toVector.map(i ⇒ i: Int), NoUnits)

  @varargs
  override def jset(v: java.lang.Integer*) = IntItem(keyName, v.map(i ⇒ i: Int).toVector, units = UnitsOfMeasure.NoUnits)
}

