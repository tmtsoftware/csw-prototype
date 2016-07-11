package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
  * The type of a head for an BooleanKey
  *
  * @param keyName the name of the key
  * @param values  the head for the key
  * @param units   the units of the head
  */
final case class BooleanItem(keyName: String, values: Vector[Boolean], units: Units) extends Item[Boolean] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
  * A key of Boolean values
  *
  * @param nameIn the name of the key
  */
final case class BooleanKey(nameIn: String) extends Key[Boolean, BooleanItem](nameIn) {

  override def set(v: Vector[Boolean], units: Units = NoUnits) = BooleanItem(keyName, v, units)

  override def set(v: Boolean*) = BooleanItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

