package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a head for an LongKey
 *
 * @param keyName the name of the key
 * @param values   the head for the key
 * @param units   the units of the head
 */
final case class LongItem(keyName: String, values: Vector[Long], units: Units) extends Item[Long /*, java.lang.Long*/ ] {
  //override def jvalues: java.util.List[java.lang.Long] = values.map(i ⇒ i: java.lang.Long).asJava

  //override def jvalue(index: Int): java.lang.Long = values(index)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)

  //override def jget(index: Int): java.util.Optional[java.lang.Long] = get(index).map(i ⇒ i: java.lang.Long).asJava

  //override def jvalue: java.lang.Long = values(0)
}

/**
 * A key of Long values
 *
 * @param nameIn the name of the key
 */
final case class LongKey(nameIn: String) extends Key[Long, LongItem /*java.lang.Long*/ ](nameIn) {

  override def set(v: Vector[Long], units: Units = NoUnits) = LongItem(keyName, v, units)

  override def set(v: Long*) = LongItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  //override def jset(v: java.util.List[java.lang.Long]): LongItem = LongItem(keyName, v.asScala.toVector.map(i ⇒ i: Long), NoUnits)

  //@varargs
  //override def jset(v: java.lang.Long*) = LongItem(keyName, v.map(i ⇒ i: Long).toVector, units = UnitsOfMeasure.NoUnits)
}

