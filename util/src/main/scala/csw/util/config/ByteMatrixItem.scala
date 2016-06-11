package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala equivalent of a 2d array of Bytes
 */
case class ByteMatrix(value: Vector[Vector[Byte]]) {
  def toJava: JByteMatrix = JByteMatrix(
    value.map(v ⇒ v.map(i ⇒ i: java.lang.Byte).asJava).asJava
  )
}
case object ByteMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ByteMatrix.apply)
}

/**
 * A Java equivalent of a 2d array of Doubles
 */
case class JByteMatrix(value: java.util.List[java.util.List[java.lang.Byte]]) {
  def toScala: ByteMatrix = ByteMatrix(
    value.asScala.toVector.map(l ⇒ l.asScala.toVector.map(i ⇒ i: Byte))
  )
}

case object JByteMatrix {
  /**
   * Initialize from an array of arrays of bytes
   */
  def fromArray(ar: Array[Array[Byte]]): JByteMatrix = JByteMatrix(ar.toVector.map(a ⇒ a.toVector.map(i ⇒ i: java.lang.Byte).asJava).asJava)
}

/**
 * The type of a value for an ByteMatrixKey: One or more 2d arrays (implemented as ByteMatrix)
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class ByteMatrixItem(keyName: String, values: Vector[ByteMatrix], units: Units) extends Item[ByteMatrix, JByteMatrix] {

  override def jvalues: java.util.List[JByteMatrix] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JByteMatrix = values(index).toJava

  override def jget(index: Int): java.util.Optional[JByteMatrix] = get(index).map(_.toJava).asJava

  override def jvalue: JByteMatrix = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ByteMatrix values
 *
 * @param nameIn the name of the key
 */
final case class ByteMatrixKey(nameIn: String) extends Key[ByteMatrix, JByteMatrix](nameIn) {

  override def set(v: Vector[ByteMatrix], units: Units = NoUnits) = ByteMatrixItem(keyName, v, units)

  override def set(v: ByteMatrix*) = ByteMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JByteMatrix]): ByteMatrixItem = ByteMatrixItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JByteMatrix*) = ByteMatrixItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

