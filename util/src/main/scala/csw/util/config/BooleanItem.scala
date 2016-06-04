package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an BooleanKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class BooleanItem(keyName: String, values: Vector[Boolean], units: Units) extends Item[Boolean, java.lang.Boolean] {

  /**
   * Java API
   *
   * @return the values as a Java List
   */
  override def jvalues: java.util.List[java.lang.Boolean] = values.map(i ⇒ i: java.lang.Boolean).asJava

  override def jvalue(index: Int): java.lang.Boolean = values(index)

  override def jget(index: Int): java.util.Optional[java.lang.Boolean] = get(index).map(i ⇒ i: java.lang.Boolean).asJava

  override def jvalue: java.lang.Boolean = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Boolean values
 *
 * @param nameIn the name of the key
 */
final case class BooleanKey(nameIn: String) extends Key[Boolean, java.lang.Boolean](nameIn) {

  override def set(v: Vector[Boolean], units: Units = NoUnits) = BooleanItem(keyName, v, units)

  override def set(v: Boolean*) = BooleanItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Boolean]): BooleanItem = BooleanItem(keyName, v.asScala.toVector.map(i ⇒ i: Boolean), NoUnits)

  @varargs
  override def jset(v: java.lang.Boolean*) = BooleanItem(keyName, v.map(i ⇒ i: Boolean).toVector, units = UnitsOfMeasure.NoUnits)
}

