package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Array of Shorts
 */
case class ShortArray(value: Array[Short]) {
  import ArrayAndMatrixEquality._

  override def toString = value.mkString("(", ",", ")")

  def apply(idx: Int) = value(idx)

  override def canEqual(other: Any) = other.isInstanceOf[ShortArray]

  override def equals(other: Any) = other match {
    case that: ShortArray ⇒
      this.canEqual(that) && deepArrayEquals(this.value, that.value)
    case _ ⇒ false
  }
}
case object ShortArray extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ShortArray.apply)

  implicit def create(value: Array[Short]): ShortArray = ShortArray(value)
}

/**
 * The type of a head for a ShortArrayKey: One or more arrays of Short
 *
 * @param keyName the name of the key
 * @param values   the head for the key
 * @param units   the units of the head
 */
final case class ShortArrayItem(keyName: String, values: Vector[ShortArray], units: Units) extends Item[ShortArray] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ShortArray values
 *
 * @param nameIn the name of the key
 */
final case class ShortArrayKey(nameIn: String) extends Key[ShortArray, ShortArrayItem](nameIn) {

  override def set(v: Vector[ShortArray], units: Units = NoUnits) = ShortArrayItem(keyName, v, units)

  override def set(v: ShortArray*) = ShortArrayItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

