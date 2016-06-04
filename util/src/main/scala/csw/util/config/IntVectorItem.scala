package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import spray.json.DefaultJsonProtocol

import scala.compat.java8.OptionConverters._

/**
 * A Scala Vector of Ints
 */
case class IntVector(value: Vector[Int]) {
  def toJava: JIntVector = JIntVector(
    value.map(d ⇒ d: java.lang.Integer).asJava
  )
}
case object IntVector extends DefaultJsonProtocol {
  implicit def format = jsonFormat1(IntVector.apply)
}

/**
 * A Java List of Ints
 */
case class JIntVector(value: java.util.List[java.lang.Integer]) {
  def toScala: IntVector = IntVector(
    value.asScala.toVector.map(d ⇒ d: Int)
  )
}

/**
 * The type of a value for a IntVectorKey: One or more vectors of Int
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class IntVectorItem(keyName: String, values: Vector[IntVector], units: Units) extends Item[IntVector, JIntVector] {

  override def jvalues: java.util.List[JIntVector] = values.map(_.toJava).asJava

  override def jvalue(index: Int): JIntVector = values(index).toJava

  override def jget(index: Int): java.util.Optional[JIntVector] = get(index).map(_.toJava).asJava

  override def jvalue: JIntVector = values(0).toJava

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key for IntVector values
 *
 * @param nameIn the name of the key
 */
final case class IntVectorKey(nameIn: String) extends Key[IntVector, JIntVector](nameIn) {

  override def set(v: Vector[IntVector], units: Units = NoUnits) = IntVectorItem(keyName, v, units)

  override def set(v: IntVector*) = IntVectorItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[JIntVector]): IntVectorItem = IntVectorItem(keyName, v.asScala.toVector.map(_.toScala), NoUnits)

  @varargs
  override def jset(v: JIntVector*) = IntVectorItem(keyName, v.map(_.toScala).toVector, units = UnitsOfMeasure.NoUnits)
}

