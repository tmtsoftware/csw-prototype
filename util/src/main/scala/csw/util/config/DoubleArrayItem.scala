package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * A Scala Array of Doubles
 */
case class DoubleArray(data: Array[Double]) {
  import ArrayAndMatrixEquality._

  override def toString = data.mkString("(", ",", ")")

  /**
   * Gets the value at the given index
   */
  def apply(idx: Int) = data(idx)

  override def canEqual(other: Any) = other.isInstanceOf[DoubleArray]

  override def equals(other: Any) = other match {
    case that: DoubleArray =>
      this.canEqual(that) && deepArrayEquals(this.data, that.data)
    case _ => false
  }
}

case object DoubleArray extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(DoubleArray.apply)

  implicit def create(value: Array[Double]): DoubleArray = DoubleArray(value)
}

/**
 * The type of a value for a DoubleVectorKey: One or more vectors of Double
 *
 * @param keyName the name of the key
 * @param values  the value for the key
 * @param units   the units of the value
 */
final case class DoubleArrayItem(keyName: String, values: Vector[DoubleArray], units: Units) extends Item[DoubleArray] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for DoubleArray values
 *
 * @param nameIn the name of the key
 */
final case class DoubleArrayKey(nameIn: String) extends Key[DoubleArray, DoubleArrayItem](nameIn) {

  override def set(v: Vector[DoubleArray], units: Units = NoUnits) = DoubleArrayItem(keyName, v, units)

  override def set(v: DoubleArray*) = DoubleArrayItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

