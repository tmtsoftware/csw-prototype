package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * The type of a value for an StringKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class StringItem(keyName: String, values: Vector[String], units: Units) extends Item[String] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of String values
 *
 * @param nameIn the name of the key
 */
final case class StringKey(nameIn: String) extends Key[String, StringItem](nameIn) {

  override def set(v: Vector[String], units: Units = NoUnits) = StringItem(keyName, v, units)

  override def set(v: String*) = StringItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

