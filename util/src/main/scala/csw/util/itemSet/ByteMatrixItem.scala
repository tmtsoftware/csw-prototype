package csw.util.itemSet

import csw.util.itemSet.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * A Scala equivalent of a 2d array of Bytes
 */
case class ByteMatrix(data: Array[Array[Byte]]) {
  import ArrayAndMatrixEquality._

  override def toString = (for (l <- data) yield l.mkString("(", ",", ")")).mkString("(", ",", ")")

  /**
   * Gets the value at the given row and column
   */
  def apply(row: Int, col: Int) = data(row)(col)

  override def canEqual(other: Any) = other.isInstanceOf[ByteMatrix]

  override def equals(other: Any) = other match {
    case that: ByteMatrix =>
      this.canEqual(that) && deepMatrixValueEquals(this.data, that.data)
    case _ => false
  }
}

case object ByteMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ByteMatrix.apply)

  implicit def create(value: Array[Array[Byte]]): ByteMatrix = ByteMatrix(value)
}

/**
 * The type of a value for an ByteMatrixKey: One or more 2d arrays (implemented as ByteMatrix)
 *
 * @param keyName the name of the key
 * @param values  the value for the key
 * @param units   the units of the value
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

