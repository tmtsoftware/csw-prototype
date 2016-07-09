package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a head for an FloatKey
 *
 * @param keyName the name of the key
 * @param values   the head for the key
 * @param units   the units of the head
 */
final case class FloatItem(keyName: String, values: Vector[Float], units: Units) extends Item[Float /*, java.lang.Float*/] {

  //override def jvalues: java.util.List[java.lang.Float] = values.map(i ⇒ i: java.lang.Float).asJava

  //override def jvalue(index: Int): java.lang.Float = values(index)

  //override def jget(index: Int): java.util.Optional[java.lang.Float] = get(index).map(i ⇒ i: java.lang.Float).asJava

  //override def jvalue: java.lang.Float = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Float values
 *
 * @param nameIn the name of the key
 */
final case class FloatKey(nameIn: String) extends Key[Float, FloatItem /*java.lang.Float*/](nameIn) {

  override def set(v: Vector[Float], units: Units = NoUnits) = FloatItem(keyName, v, units)

  override def set(v: Float*) = FloatItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  //override def jset(v: java.util.List[java.lang.Float]): FloatItem = FloatItem(keyName, v.asScala.toVector.map(i ⇒ i: Float), NoUnits)

  //@varargs
  //override def jset(v: java.lang.Float*) = FloatItem(keyName, v.map(i ⇒ i: Float).toVector, units = UnitsOfMeasure.NoUnits)
}

