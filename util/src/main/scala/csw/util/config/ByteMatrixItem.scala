package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * A Scala equivalent of a 2d array of Bytes
 */
case class ByteMatrix(value: Array[Array[Byte]]) {
  import ArrayAndMatrixEquality._

  override def toString = (for (l ← value) yield l.mkString("(", ",", ")")).mkString("(", ",", ")")

  def apply(row: Int, col: Int) = value(row)(col)

  override def canEqual(other: Any) = other.isInstanceOf[ByteMatrix]

  override def equals(other: Any) = other match {
    case that: ByteMatrix ⇒
      this.canEqual(that) && deepMatrixValueEquals(this.value, that.value)
    case _ ⇒ false
  }
}

case object ByteMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ByteMatrix.apply)

  implicit def create(value: Array[Array[Byte]]): ByteMatrix = ByteMatrix(value)
}

/**
 * The type of a head for an ByteMatrixKey: One or more 2d arrays (implemented as ByteMatrix)
 *
 * @param keyName the name of the key
 * @param values  the head for the key
 * @param units   the units of the head
 */
final case class ByteMatrixItem(keyName: String, values: Vector[ByteMatrix], units: Units) extends Item[ByteMatrix] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ByteMatrix values
 *
 * @param nameIn the name of the key
 */
final case class ByteMatrixKey(nameIn: String) extends Key[ByteMatrix, ByteMatrixItem](nameIn) {

  override def set(v: Vector[ByteMatrix], units: Units = NoUnits) = ByteMatrixItem(keyName, v, units)

  override def set(v: ByteMatrix*) = ByteMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

