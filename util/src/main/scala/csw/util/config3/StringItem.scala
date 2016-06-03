package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an StringKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class StringItem(keyName: String, values: Vector[String], units: Units) extends Item[String, java.lang.String] {
  override def jvalues: java.util.List[java.lang.String] = values.map(i ⇒ i: java.lang.String).asJava

  override def jvalue(index: Int): java.lang.String = values(index)

  override def jget(index: Int): java.util.Optional[java.lang.String] = get(index).map(i ⇒ i: java.lang.String).asJava

  override def jvalue: java.lang.String = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of String values
 *
 * @param nameIn the name of the key
 */
final case class StringKey(nameIn: String) extends Key[String, java.lang.String](nameIn) {

  override def set(v: Vector[String], units: Units = NoUnits) = StringItem(keyName, v, units)

  override def set(v: String*) = StringItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.String]): StringItem = StringItem(keyName, v.asScala.toVector.map(i ⇒ i: String), NoUnits)

  @varargs
  override def jset(v: java.lang.String*) = StringItem(keyName, v.map(i ⇒ i: String).toVector, units = UnitsOfMeasure.NoUnits)
}

