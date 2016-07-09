package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
  * A Scala equivalent of a 2d array of Doubles
  */
case class DoubleMatrix(value: Array[Array[Double]]) {
  import ArrayAndMatrixEquality._

  override def toString = (for (l <- value) yield l.mkString("(", ",", ")")).mkString("(", ",", ")")

  def apply(row: Int, col: Int) = value(row)(col)

  override def canEqual(other: Any) = other.isInstanceOf[DoubleMatrix]

  override def equals(other: Any) = other match {
    case that: DoubleMatrix =>
      this.canEqual(that) && deepMatrixValueEquals(this.value, that.value)
    case _ => false
  }
}

case object DoubleMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(DoubleMatrix.apply)

  implicit def create(value: Array[Array[Double]]):DoubleMatrix = DoubleMatrix(value)
}

/**
  * The type of a head for a DoubleMatrixKey: One or more 2d arrays (implemented as DoubleMatrix)
  *
  * @param keyName the name of the key
  * @param values  the head for the key
  * @param units   the units of the head
  */
final case class DoubleMatrixItem(keyName: String, values: Vector[DoubleMatrix], units: Units) extends Item[DoubleMatrix] {
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
  * A key for DoubleMatrix values
  *
  * @param nameIn the name of the key
  */
final case class DoubleMatrixKey(nameIn: String) extends Key[DoubleMatrix, DoubleMatrixItem](nameIn) {

  override def set(v: Vector[DoubleMatrix], units: Units = NoUnits) = DoubleMatrixItem(keyName, v, units)

  override def set(v: DoubleMatrix*) = DoubleMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

