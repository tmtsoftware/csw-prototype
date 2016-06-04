package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala equivalent of a 2d array of Doubles
 */
case class DoubleMatrix(value: Vector[Vector[Double]]) {
  def toJava: JDoubleMatrix = JDoubleMatrix(
    value.map(v ⇒ v.map(d ⇒ d: java.lang.Double).asJava).asJava
  )
}
case object DoubleMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(DoubleMatrix.apply)
}

/**
 * A Java equivalent of a 2d array of Doubles
 */
case class JDoubleMatrix(value: java.util.List[java.util.List[java.lang.Double]]) {
  def toScala: DoubleMatrix = DoubleMatrix(
    value.asScala.toVector.map(l ⇒ l.asScala.toVector.map(d ⇒ d: Double))
  )
}

/**
 * The type of a value for a DoubleMatrixKey: One or more 2d arrays (implemented as DoubleMatrix)
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class DoubleMatrixItem(keyName: String, values: Vector[DoubleMatrix], units: Units) extends Item[DoubleMatrix, JDoubleMatrix] {

  override def jvalues: java.util.List[JDoubleMatrix] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JDoubleMatrix = values(index).toJava

  override def jget(index: Int): java.util.Optional[JDoubleMatrix] = get(index).map(_.toJava).asJava

  override def jvalue: JDoubleMatrix = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for DoubleMatrix values
 *
 * @param nameIn the name of the key
 */
final case class DoubleMatrixKey(nameIn: String) extends Key[DoubleMatrix, JDoubleMatrix](nameIn) {

  override def set(v: Vector[DoubleMatrix], units: Units = NoUnits) = DoubleMatrixItem(keyName, v, units)

  override def set(v: DoubleMatrix*) = DoubleMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JDoubleMatrix]): DoubleMatrixItem = DoubleMatrixItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JDoubleMatrix*) = DoubleMatrixItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

