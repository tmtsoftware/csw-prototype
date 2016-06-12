package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Vector of Longs
 */
case class LongVector(value: Vector[Long]) {
  def toJava: JLongVector = JLongVector(
    value.map(d ⇒ d: java.lang.Long).asJava
  )
}
case object LongVector extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(LongVector.apply)
}

/**
 * A Java List of Longs
 */
case class JLongVector(value: java.util.List[java.lang.Long]) {
  def toScala: LongVector = LongVector(
    value.asScala.toVector.map(d ⇒ d: Long)
  )
}

case object JLongVector {
  /**
   * Java API: Initialize from an array of longs
   */
  def fromArray(ar: Array[Long]): JLongVector = JLongVector(ar.toVector.map(i ⇒ i: java.lang.Long).asJava)
}

/**
 * The type of a value for a LongVectorKey: One or more vectors of Long
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class LongVectorItem(keyName: String, values: Vector[LongVector], units: Units) extends Item[LongVector, JLongVector] {

  override def jvalues: java.util.List[JLongVector] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JLongVector = values(index).toJava

  override def jget(index: Int): java.util.Optional[JLongVector] = get(index).map(_.toJava).asJava

  override def jvalue: JLongVector = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for LongVector values
 *
 * @param nameIn the name of the key
 */
final case class LongVectorKey(nameIn: String) extends Key[LongVector, JLongVector](nameIn) {

  override def set(v: Vector[LongVector], units: Units = NoUnits) = LongVectorItem(keyName, v, units)

  override def set(v: LongVector*) = LongVectorItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JLongVector]): LongVectorItem = LongVectorItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JLongVector*) = LongVectorItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

