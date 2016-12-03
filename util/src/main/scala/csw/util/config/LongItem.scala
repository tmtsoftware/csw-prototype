package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * The type of a value for an LongKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class LongItem(keyName: String, values: Vector[Long], units: Units) extends Item[Long] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Long values
 *
 * @param nameIn the name of the key
 */
final case class LongKey(nameIn: String) extends Key[Long, LongItem](nameIn) {

  override def set(v: Vector[Long], units: Units = NoUnits) = LongItem(keyName, v, units)

  override def set(v: Long*) = LongItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

