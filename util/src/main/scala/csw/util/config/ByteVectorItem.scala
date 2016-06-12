package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Vector of Bytes
 */
case class ByteVector(value: Vector[Byte]) {
  def toJava: JByteVector = JByteVector(
    value.map(d ⇒ d: java.lang.Byte).asJava
  )
}
case object ByteVector extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ByteVector.apply)
}

/**
 * A Java List of Bytes
 */
case class JByteVector(value: java.util.List[java.lang.Byte]) {
  def toScala: ByteVector = ByteVector(
    value.asScala.toVector.map(d ⇒ d: Byte)
  )
}

case object JByteVector {
  /**
   * Java API: Initialize from an array of bytes
   */
  def fromArray(ar: Array[Byte]): JByteVector = JByteVector(ar.toVector.map(i ⇒ i: java.lang.Byte).asJava)
}

/**
 * The type of a value for a ByteVectorKey: One or more vectors of Byte
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class ByteVectorItem(keyName: String, values: Vector[ByteVector], units: Units) extends Item[ByteVector, JByteVector] {

  override def jvalues: java.util.List[JByteVector] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JByteVector = values(index).toJava

  override def jget(index: Int): java.util.Optional[JByteVector] = get(index).map(_.toJava).asJava

  override def jvalue: JByteVector = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ByteVector values
 *
 * @param nameIn the name of the key
 */
final case class ByteVectorKey(nameIn: String) extends Key[ByteVector, JByteVector](nameIn) {

  override def set(v: Vector[ByteVector], units: Units = NoUnits) = ByteVectorItem(keyName, v, units)

  override def set(v: ByteVector*) = ByteVectorItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JByteVector]): ByteVectorItem = ByteVectorItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JByteVector*) = ByteVectorItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

