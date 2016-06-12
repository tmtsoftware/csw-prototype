package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala equivalent of a 2d array of Shorts
 */
case class ShortMatrix(value: Vector[Vector[Short]]) {
  def toJava: JShortMatrix = JShortMatrix(
    value.map(v ⇒ v.map(i ⇒ i: java.lang.Short).asJava).asJava
  )
}
case object ShortMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(ShortMatrix.apply)
}

/**
 * A Java equivalent of a 2d array of shorts
 */
case class JShortMatrix(value: java.util.List[java.util.List[java.lang.Short]]) {
  def toScala: ShortMatrix = ShortMatrix(
    value.asScala.toVector.map(l ⇒ l.asScala.toVector.map(i ⇒ i: Short))
  )
}

case object JShortMatrix {
  /**
   * Java API: Initialize from an array of arrays of shorts
   */
  def fromArray(ar: Array[Array[Short]]): JShortMatrix = JShortMatrix(ar.toVector.map(a ⇒ a.toVector.map(i ⇒ i: java.lang.Short).asJava).asJava)
}

/**
 * The type of a value for an ShortMatrixKey: One or more 2d arrays (implemented as ShortMatrix)
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class ShortMatrixItem(keyName: String, values: Vector[ShortMatrix], units: Units) extends Item[ShortMatrix, JShortMatrix] {

  override def jvalues: java.util.List[JShortMatrix] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JShortMatrix = values(index).toJava

  override def jget(index: Int): java.util.Optional[JShortMatrix] = get(index).map(_.toJava).asJava

  override def jvalue: JShortMatrix = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for ShortMatrix values
 *
 * @param nameIn the name of the key
 */
final case class ShortMatrixKey(nameIn: String) extends Key[ShortMatrix, JShortMatrix](nameIn) {

  override def set(v: Vector[ShortMatrix], units: Units = NoUnits) = ShortMatrixItem(keyName, v, units)

  override def set(v: ShortMatrix*) = ShortMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JShortMatrix]): ShortMatrixItem = ShortMatrixItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JShortMatrix*) = ShortMatrixItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

