package csw.util.itemSet

import csw.util.itemSet.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * The type of a value for an CharKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class CharItem(keyName: String, values: Vector[Char], units: Units) extends Item[Char] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Char values
 *
 * @param nameIn the name of the key
 */
final case class CharKey(nameIn: String) extends Key[Char, CharItem /*Character*/ ](nameIn) {

  override def set(v: Vector[Char], units: Units = NoUnits) = CharItem(keyName, v, units)

  override def set(v: Char*) = CharItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}
