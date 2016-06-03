package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an DoubleKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class DoubleItem(keyName: String, values: Vector[Double], units: Units) extends Item[Double, java.lang.Double] {

  override def jvalues: java.util.List[java.lang.Double] = values.map(i ⇒ i: java.lang.Double).asJava

  override def jvalue(index: Int): java.lang.Double = values(index)

  override def jget(index: Int): java.util.Optional[java.lang.Double] = get(index).map(i ⇒ i: java.lang.Double).asJava

  override def jvalue: java.lang.Double = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Double values
 *
 * @param nameIn the name of the key
 */
final case class DoubleKey(nameIn: String) extends Key[Double, java.lang.Double](nameIn) {

  override def set(v: Vector[Double], units: Units = NoUnits) = DoubleItem(keyName, v, units)

  override def set(v: Double*) = DoubleItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Double]): DoubleItem = DoubleItem(keyName, v.asScala.toVector.map(i ⇒ i: Double), NoUnits)

  @varargs
  override def jset(v: java.lang.Double*) = DoubleItem(keyName, v.map(i ⇒ i: Double).toVector, units = UnitsOfMeasure.NoUnits)
}

