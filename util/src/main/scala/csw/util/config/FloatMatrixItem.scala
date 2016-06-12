package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala equivalent of a 2d array of Floats
 */
case class FloatMatrix(value: Vector[Vector[Float]]) {
  def toJava: JFloatMatrix = JFloatMatrix(
    value.map(v ⇒ v.map(d ⇒ d: java.lang.Float).asJava).asJava
  )
}
case object FloatMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(FloatMatrix.apply)
}

/**
 * A Java equivalent of a 2d array of Floats
 */
case class JFloatMatrix(value: java.util.List[java.util.List[java.lang.Float]]) {
  def toScala: FloatMatrix = FloatMatrix(
    value.asScala.toVector.map(l ⇒ l.asScala.toVector.map(d ⇒ d: Float))
  )
}

/**
 * The type of a value for a FloatMatrixKey: One or more 2d arrays (implemented as FloatMatrix)
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class FloatMatrixItem(keyName: String, values: Vector[FloatMatrix], units: Units) extends Item[FloatMatrix, JFloatMatrix] {

  override def jvalues: java.util.List[JFloatMatrix] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JFloatMatrix = values(index).toJava

  override def jget(index: Int): java.util.Optional[JFloatMatrix] = get(index).map(_.toJava).asJava

  override def jvalue: JFloatMatrix = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for FloatMatrix values
 *
 * @param nameIn the name of the key
 */
final case class FloatMatrixKey(nameIn: String) extends Key[FloatMatrix, JFloatMatrix](nameIn) {

  override def set(v: Vector[FloatMatrix], units: Units = NoUnits) = FloatMatrixItem(keyName, v, units)

  override def set(v: FloatMatrix*) = FloatMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JFloatMatrix]): FloatMatrixItem = FloatMatrixItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JFloatMatrix*) = FloatMatrixItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

