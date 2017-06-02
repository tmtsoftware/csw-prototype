package csw.util.itemSet

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.itemSet.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an IntKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class IntItem(keyName: String, values: Vector[Int], units: Units) extends Item[Int] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Int values
 *
 * @param nameIn the name of the key
 */
final case class IntKey(nameIn: String) extends Key[Int, IntItem](nameIn) {

  override def set(v: Vector[Int], units: Units = NoUnits) = IntItem(keyName, v, units)

  override def set(v: Int*) = IntItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

