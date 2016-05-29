package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.Units

/**
  * The type of a value for an GenericKey
  *
  * @param keyName the name of the key
  * @param value   the value for the key
  * @param units   the units of the value
  * @param fsj       a function that converts the Scala value to a Java Value
  */
final case class GenericItem[+S, +J](keyName: String, value: Vector[S], units: Units, fsj: S => J) extends Item[S, J] {
  /**
    * Java API
    *
    * @return the values as a Scala Vector
    */
  override def jvalue: Vector[J] = value.map(fsj)

//  /**
//    * Java API
//    *
//    * @return the values as a Java List
//    */
//  def jvalues: java.util.List[J] = jvalue.asJava

  /**
    * Java API
    *
    * @return the value at the given index
    */
  override def jget(index: Int): J = fsj(value(index))

  /**
    * Set the units of the value
    *
    * @param unitsIn the units to set
    * @return a copy of this item with the given units set
    */
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
  * A key of S values
  *
  * @param nameIn the name of the key
  * @param fsj    a function that converts the Scala value to a Java Value
  * @param fjs    a function that converts the Java value to a Scala Value
  */
case class GenericKey[S, J](nameIn: String, fsj: S => J, fjs: J => S) extends Key[S, J](nameIn) {

  /**
    * Sets the values for the key
    *
    * @param v     the values
    * @param units the units of the values
    * @return a new item containing the key name, values and units
    */
  override def set(v: Vector[S], units: Units) = GenericItem(keyName, v, units, fsj)

  /**
    * Sets the values for the key using a variable number of arguments
    *
    * @param v the values
    * @return a new item containing the key name, values and no units
    */
  override def set(v: S*) = GenericItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits, fsj)

  /**
    * Java API to set the values for a key
    *
    * @param v     the values as a java list
    * @param units the units of the values
    * @return a new item containing the key name, values and units
    */
  def jset(v: Vector[J], units: Units) = GenericItem(keyName, v.map(fjs), units, fsj)

  /**
    * Java API to set the values for a key
    *
    * @param v     the values as a java list
    * @param units the units of the values
    * @return a new item containing the key name, values and units
    */
  def jset(v: java.util.List[J], units: Units) = jset(v.asScala.toVector, units)

  /**
    * Java API: Sets the values for the key using a variable number of arguments
    *
    * @param v the values
    * @return a new item containing the key name, values and no units
    */
  @varargs
  override def jset(v: J*) = GenericItem(keyName, v.map(fjs).toVector, units = UnitsOfMeasure.NoUnits, fsj)
}

