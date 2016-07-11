package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * A Scala equivalent of a 2d array of Shorts
 */
case class ShortMatrix(value: Array[Array[Short]]) {
  import ArrayAndMatrixEquality._

  override def toString = (for (l ← value) yield l.mkString("(", ",", ")")).mkString("(", ",", ")")

  def apply(row: Int, col: Int) = value(row)(col)

  override def canEqual(other: Any) = other.isInstanceOf[ShortMatrix]

  override def equals(other: Any) = other match {
    case that: ShortMatrix ⇒
      this.canEqual(that) && deepMatrixValueEquals(this.value, that.value)
    case _ ⇒ false
  }
}

//case class ShortMatrix(head Vec)
case object ShortMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ShortMatrix.apply)

  implicit def create(value: Array[Array[Short]]): ShortMatrix = ShortMatrix(value)
}

/**
 * A key for ShortMatrix values
 *
 * @param nameIn the name of the key
 */
final case class ShortMatrixKey(nameIn: String) extends Key[ShortMatrix, ShortMatrixItem](nameIn) {

  override def set(v: Vector[ShortMatrix], units: Units = NoUnits) = ShortMatrixItem(keyName, v, units)

  override def set(v: ShortMatrix*) = ShortMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

/**
 * The type of a head for an ShortMatrixKey: One or more 2d arrays (implemented as ShortMatrix)
 *
 * @param keyName the name of the key
 * @param values   the head for the key
 * @param units   the units of the head
 */
final case class ShortMatrixItem(keyName: String, values: Vector[ShortMatrix], units: Units) extends Item[ShortMatrix] {
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}
