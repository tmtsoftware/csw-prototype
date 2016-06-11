package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Vector of Doubles
 */
case class DoubleVector(value: Vector[Double]) {
  def toJava: JDoubleVector = JDoubleVector(
    value.map(d ⇒ d: java.lang.Double).asJava
  )
}
case object DoubleVector extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(DoubleVector.apply)
}

/**
 * A Java List of Doubles
 */
case class JDoubleVector(value: java.util.List[java.lang.Double]) {
  def toScala: DoubleVector = DoubleVector(
    value.asScala.toVector.map(d ⇒ d: Double)
  )
}

case object JDoubleVector {
  /**
   * Initialize from an array of doubles
   */
  def fromArray(ar: Array[Double]): JDoubleVector = JDoubleVector(ar.toVector.map(i ⇒ i: java.lang.Double).asJava)
}

/**
 * The type of a value for a DoubleVectorKey: One or more vectors of Double
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class DoubleVectorItem(keyName: String, values: Vector[DoubleVector], units: Units) extends Item[DoubleVector, JDoubleVector] {

  override def jvalues: java.util.List[JDoubleVector] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JDoubleVector = values(index).toJava

  override def jget(index: Int): java.util.Optional[JDoubleVector] = get(index).map(_.toJava).asJava

  override def jvalue: JDoubleVector = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for DoubleVector values
 *
 * @param nameIn the name of the key
 */
final case class DoubleVectorKey(nameIn: String) extends Key[DoubleVector, JDoubleVector](nameIn) {

  override def set(v: Vector[DoubleVector], units: Units = NoUnits) = DoubleVectorItem(keyName, v, units)

  override def set(v: DoubleVector*) = DoubleVectorItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JDoubleVector]): DoubleVectorItem = DoubleVectorItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JDoubleVector*) = DoubleVectorItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

