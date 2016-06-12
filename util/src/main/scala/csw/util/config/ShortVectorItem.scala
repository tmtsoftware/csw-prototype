package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Vector of Shorts
 */
case class ShortVector(value: Vector[Short]) {
  def toJava: JShortVector = JShortVector(
    value.map(d ⇒ d: java.lang.Short).asJava
  )
}
case object ShortVector extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ShortVector.apply)
}

/**
 * A Java List of Shorts
 */
case class JShortVector(value: java.util.List[java.lang.Short]) {
  def toScala: ShortVector = ShortVector(
    value.asScala.toVector.map(d ⇒ d: Short)
  )
}

case object JShortVector {
  /**
   * Java API: Initialize from an array of shorts
   */
  def fromArray(ar: Array[Short]): JShortVector = JShortVector(ar.toVector.map(i ⇒ i: java.lang.Short).asJava)
}

/**
 * The type of a value for a ShortVectorKey: One or more vectors of Short
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class ShortVectorItem(keyName: String, values: Vector[ShortVector], units: Units) extends Item[ShortVector, JShortVector] {

  override def jvalues: java.util.List[JShortVector] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JShortVector = values(index).toJava

  override def jget(index: Int): java.util.Optional[JShortVector] = get(index).map(_.toJava).asJava

  override def jvalue: JShortVector = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ShortVector values
 *
 * @param nameIn the name of the key
 */
final case class ShortVectorKey(nameIn: String) extends Key[ShortVector, JShortVector](nameIn) {

  override def set(v: Vector[ShortVector], units: Units = NoUnits) = ShortVectorItem(keyName, v, units)

  override def set(v: ShortVector*) = ShortVectorItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JShortVector]): ShortVectorItem = ShortVectorItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JShortVector*) = ShortVectorItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

