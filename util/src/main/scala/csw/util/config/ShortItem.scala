package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a head for an ShortKey
 *
 * @param keyName the name of the key
 * @param values   the head for the key
 * @param units   the units of the head
 */
final case class ShortItem(keyName: String, values: Vector[Short], units: Units) extends Item[Short /*, java.lang.Short*/] {

  //override def jvalues: java.util.List[java.lang.Short] = values.map(i ⇒ i: java.lang.Short).asJava

//  override def jvalue(index: Int): java.lang.Short = values(index)

  //override def jget(index: Int): java.util.Optional[java.lang.Short] = get(index).map(i ⇒ i: java.lang.Short).asJava

  //override def jvalue: java.lang.Short = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Short values
 *
 * @param nameIn the name of the key
 */
final case class ShortKey(nameIn: String) extends Key[Short, ShortItem /*java.lang.Short*/](nameIn) {

  override def set(v: Vector[Short], units: Units = NoUnits) = ShortItem(keyName, v, units)

  override def set(v: Short*) = ShortItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  //override def jset(v: java.util.List[java.lang.Short]): ShortItem = ShortItem(keyName, v.asScala.toVector.map(i ⇒ i: Short), NoUnits)

  //@varargs
  //override def jset(v: java.lang.Short*) = ShortItem(keyName, v.map(i ⇒ i: Short).toVector, units = UnitsOfMeasure.NoUnits)
}

