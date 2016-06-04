package csw.util.config

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config.UnitsOfMeasure.{NoUnits, Units}
import scala.compat.java8.OptionConverters._

/**
 * The type of a value for an CharKey
 *
 * @param keyName the name of the key
 * @param values   the value for the key
 * @param units   the units of the value
 */
final case class CharItem(keyName: String, values: Vector[Char], units: Units) extends Item[Char, Character] {
  override def jvalues: java.util.List[Character] = values.map(i ⇒ i: Character).asJava

  override def jvalue(index: Int): Character = values(index)

  override def jget(index: Int): java.util.Optional[Character] = get(index).map(i ⇒ i: Character).asJava

  override def jvalue: Character = values(0)

  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
 * A key of Char values
 *
 * @param nameIn the name of the key
 */
final case class CharKey(nameIn: String) extends Key[Char, Character](nameIn) {

  override def set(v: Vector[Char], units: Units = NoUnits) = CharItem(keyName, v, units)

  override def set(v: Char*) = CharItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  @varargs
  override def jset(v: Character*) = CharItem(keyName, v.map(i ⇒ i: Char).toVector, units = UnitsOfMeasure.NoUnits)

  override def jset(v: java.util.List[java.lang.Character]): CharItem = CharItem(keyName, v.asScala.toVector.map(i ⇒ i: Char), NoUnits)
}
