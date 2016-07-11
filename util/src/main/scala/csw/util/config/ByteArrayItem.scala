package csw.util.config

import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Vector
import scala.language.implicitConversions

/**
 * A Scala Vector of Bytes
 */
case class ByteArray(value: Array[Byte]) {
  import ArrayAndMatrixEquality._

  override def toString = value.mkString("(", ",", ")")

  def apply(idx: Int) = value(idx)

  override def canEqual(other: Any) = other.isInstanceOf[ByteArray]

  override def equals(other: Any) = other match {
    case that: ByteArray ⇒
      this.canEqual(that) && deepArrayEquals(this.value, that.value)
    case _ ⇒ false
  }
}

case object ByteArray extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ByteArray.apply)

  implicit def create(value: Array[Byte]): ByteArray = ByteArray(value)
}

/**
 * The type of a value for a ByteArrayKey: One or more arrays of Byte
 *
 * @param keyName the name of the key
 * @param values  the value for the key
 * @param units   the units of the value
 */
final case class ByteArrayItem(keyName: String, values: Vector[ByteArray], units: Units) extends Item[ByteArray] {

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ByteArray values
 *
 * @param nameIn the name of the key
 */
final case class ByteArrayKey(nameIn: String) extends Key[ByteArray, ByteArrayItem](nameIn) {

  override def set(v: Vector[ByteArray], units: Units = NoUnits) = ByteArrayItem(keyName, v, units)

  override def set(v: ByteArray*) = ByteArrayItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
}

