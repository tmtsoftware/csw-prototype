package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}

/**
 * The type of a value for an StringKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class StringItem(keyName: String, values: Vector[String], units: Units) extends Item[String, java.lang.String] {
  /**
   * Java API
   *
   * @return the values as a Java List
   */
  def jvalues: java.util.List[java.lang.String] = values.map(i ⇒ i: java.lang.String).asJava

  override def jget(index: Int): java.lang.String = values(index)

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

