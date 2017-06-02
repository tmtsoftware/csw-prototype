package csw.util.itemSet

import csw.util.itemSet.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * The type of a value for an FloatKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class FloatItem(keyName: String, values: Vector[Float], units: Units) extends Item[Float] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Float values
 *
 * @param nameIn the name of the key
 */
final case class FloatKey(nameIn: String) extends Key[Float, FloatItem](nameIn) {

  override def set(v: Vector[Float], units: Units = NoUnits) = FloatItem(keyName, v, units)

  override def set(v: Float*) = FloatItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

