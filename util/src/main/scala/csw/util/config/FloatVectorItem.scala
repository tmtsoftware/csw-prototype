package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Vector of Floats
 */
case class FloatVector(value: Vector[Float]) {
  def toJava: JFloatVector = JFloatVector(
    value.map(d ⇒ d: java.lang.Float).asJava
  )
}
case object FloatVector extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(FloatVector.apply)
}

/**
 * A Java List of Floats
 */
case class JFloatVector(value: java.util.List[java.lang.Float]) {
  def toScala: FloatVector = FloatVector(
    value.asScala.toVector.map(d ⇒ d: Float)
  )
}

case object JFloatVector {
  /**
   * Java API: Initialize from an array of floats
   */
  def fromArray(ar: Array[Float]): JFloatVector = JFloatVector(ar.toVector.map(i ⇒ i: java.lang.Float).asJava)
}

/**
 * The type of a value for a FloatVectorKey: One or more vectors of Float
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class FloatVectorItem(keyName: String, values: Vector[FloatVector], units: Units) extends Item[FloatVector, JFloatVector] {

  override def jvalues: java.util.List[JFloatVector] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JFloatVector = values(index).toJava

  override def jget(index: Int): java.util.Optional[JFloatVector] = get(index).map(_.toJava).asJava

  override def jvalue: JFloatVector = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for FloatVector values
 *
 * @param nameIn the name of the key
 */
final case class FloatVectorKey(nameIn: String) extends Key[FloatVector, JFloatVector](nameIn) {

  override def set(v: Vector[FloatVector], units: Units = NoUnits) = FloatVectorItem(keyName, v, units)

  override def set(v: FloatVector*) = FloatVectorItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JFloatVector]): FloatVectorItem = FloatVectorItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JFloatVector*) = FloatVectorItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

