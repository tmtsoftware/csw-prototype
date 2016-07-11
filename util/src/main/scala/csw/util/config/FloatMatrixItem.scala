package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * A Scala equivalent of a 2d array of Floats
 */
case class FloatMatrix(value: Array[Array[Float]]) {
  import ArrayAndMatrixEquality._

  override def toString = (for (l ← value) yield l.mkString("(", ",", ")")).mkString("(", ",", ")")

  def apply(row: Int, col: Int) = value(row)(col)

  override def canEqual(other: Any) = other.isInstanceOf[FloatMatrix]

  override def equals(other: Any) = other match {
    case that: FloatMatrix ⇒
      this.canEqual(that) && deepMatrixValueEquals(this.value, that.value)
    case _ ⇒ false
  }
}

case object FloatMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(FloatMatrix.apply)

  implicit def create(value: Array[Array[Float]]): FloatMatrix = FloatMatrix(value)
}

/**
 * The type of a head for a FloatMatrixKey: One or more 2d arrays (implemented as FloatMatrix)
 *
 * @param keyName the name of the key
 * @param values   the head for the key
 * @param units   the units of the head
 */
final case class FloatMatrixItem(keyName: String, values: Vector[FloatMatrix], units: Units) extends Item[FloatMatrix] {
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for FloatMatrix values
 *
 * @param nameIn the name of the key
 */
final case class FloatMatrixKey(nameIn: String) extends Key[FloatMatrix, FloatMatrixItem](nameIn) {

  override def set(v: Vector[FloatMatrix], units: Units = NoUnits) = FloatMatrixItem(keyName, v, units)

  override def set(v: FloatMatrix*) = FloatMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

