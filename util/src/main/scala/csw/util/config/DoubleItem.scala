package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
  * The type of a head for an DoubleKey
  *
  * @param keyName the name of the key
  * @param values  the head for the key
  * @param units   the units of the head
  */
final case class DoubleItem(keyName: String, values: Vector[Double], units: Units) extends Item[Double] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
  * A key of Double values
  *
  * @param nameIn the name of the key
  */
final case class DoubleKey(nameIn: String) extends Key[Double, DoubleItem /*java.lang.Double*/ ](nameIn) {

  override def set(v: Vector[Double], units: Units = NoUnits) = DoubleItem(keyName, v, units)

  override def set(v: Double*) = DoubleItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

