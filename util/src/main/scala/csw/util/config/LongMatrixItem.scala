package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala equivalent of a 2d array of Longs
 */
case class LongMatrix(value: Vector[Vector[Long]]) {
  def toJava: JLongMatrix = JLongMatrix(
    value.map(v ⇒ v.map(i ⇒ i: java.lang.Long).asJava).asJava
  )
}
case object LongMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(LongMatrix.apply)
}

/**
 * A Java equivalent of a 2d array of longs
 */
case class JLongMatrix(value: java.util.List[java.util.List[java.lang.Long]]) {
  def toScala: LongMatrix = LongMatrix(
    value.asScala.toVector.map(l ⇒ l.asScala.toVector.map(i ⇒ i: Long))
  )
}

case object JLongMatrix {
  /**
   * Java API: Initialize from an array of arrays of longs
   */
  def fromArray(ar: Array[Array[Long]]): JLongMatrix = JLongMatrix(ar.toVector.map(a ⇒ a.toVector.map(i ⇒ i: java.lang.Long).asJava).asJava)
}

/**
 * The type of a value for an LongMatrixKey: One or more 2d arrays (implemented as LongMatrix)
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class LongMatrixItem(keyName: String, values: Vector[LongMatrix], units: Units) extends Item[LongMatrix, JLongMatrix] {

  override def jvalues: java.util.List[JLongMatrix] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JLongMatrix = values(index).toJava

  override def jget(index: Int): java.util.Optional[JLongMatrix] = get(index).map(_.toJava).asJava

  override def jvalue: JLongMatrix = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for LongMatrix values
 *
 * @param nameIn the name of the key
 */
final case class LongMatrixKey(nameIn: String) extends Key[LongMatrix, JLongMatrix](nameIn) {

  override def set(v: Vector[LongMatrix], units: Units = NoUnits) = LongMatrixItem(keyName, v, units)

  override def set(v: LongMatrix*) = LongMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JLongMatrix]): LongMatrixItem = LongMatrixItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JLongMatrix*) = LongMatrixItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

