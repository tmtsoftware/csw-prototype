package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala equivalent of a 2d array of Ints
 */
case class IntMatrix(value: Vector[Vector[Int]]) {
  def toJava: JIntMatrix = JIntMatrix(
    value.map(v ⇒ v.map(i ⇒ i: java.lang.Integer).asJava).asJava
  )
}
case object IntMatrix extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(IntMatrix.apply)
}

/**
 * A Java equivalent of a 2d array of Doubles
 */
case class JIntMatrix(value: java.util.List[java.util.List[java.lang.Integer]]) {
  def toScala: IntMatrix = IntMatrix(
    value.asScala.toVector.map(l ⇒ l.asScala.toVector.map(i ⇒ i: Int))
  )
}

/**
 * The type of a value for an IntMatrixKey: One or more 2d arrays (implemented as IntMatrix)
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class IntMatrixItem(keyName: String, values: Vector[IntMatrix], units: Units) extends Item[IntMatrix, JIntMatrix] {

  override def jvalues: java.util.List[JIntMatrix] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JIntMatrix = values(index).toJava

  override def jget(index: Int): java.util.Optional[JIntMatrix] = get(index).map(_.toJava).asJava

  override def jvalue: JIntMatrix = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for IntMatrix values
 *
 * @param nameIn the name of the key
 */
final case class IntMatrixKey(nameIn: String) extends Key[IntMatrix, JIntMatrix](nameIn) {

  override def set(v: Vector[IntMatrix], units: Units = NoUnits) = IntMatrixItem(keyName, v, units)

  override def set(v: IntMatrix*) = IntMatrixItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JIntMatrix]): IntMatrixItem = IntMatrixItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JIntMatrix*) = IntMatrixItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

