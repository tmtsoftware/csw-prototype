package csw.util.config3

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions
import csw.util.config3.UnitsOfMeasure.Units

/**
  * The type of a value for an FloatKey
  *
  * @param keyName the name of the key
  * @param value   the value for the key
  * @param units   the units of the value
  */
final case class FloatItem(keyName: String, value: Vector[Float], units: Units) extends Item[Float, java.lang.Float] {
  /**
    * Java API
    *
    * @return the values as a Scala Vector
    */
  override def jvalue: Vector[java.lang.Float] = value.map(i ⇒ i: java.lang.Float)

  /**
    * Java API
    *
    * @return the values as a Java List
    */
  def jvalues: java.util.List[java.lang.Float] = jvalue.asJava

  /**
    * Java API
    *
    * @return the value at the given index
    */
  override def jget(index: Int): java.lang.Float = value(index)

  /**
    * Set the units of the value
    *
    * @param unitsIn the units to set
    * @return a copy of this item with the given units set
    */
  override def withUnits(unitsIn: Units) = copy(units = unitsIn)
}

/**
  * A key of Float values
  *
  * @param nameIn the name of the key
  */
final case class FloatKey(nameIn: String) extends Key[Float, java.lang.Float](nameIn) {

  /**
    * Sets the values for the key
    *
    * @param v     the values
    * @param units the units of the values
    * @return a new item containing the key name, values and units
    */
  override def set(v: Vector[Float], units: Units) = FloatItem(keyName, v, units)

  /**
    * Sets the values for the key using a variable number of arguments
    *
    * @param v the values
    * @return a new item containing the key name, values and no units
    */
  override def set(v: Float*) = FloatItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  /**
    * Java API to set the values for a key
    *
    * @param v     the values as a java list
    * @param units the units of the values
    * @return a new item containing the key name, values and units
    */
  def jset(v: Vector[java.lang.Float], units: Units) = FloatItem(keyName, v.map(i ⇒ i: Float), units)

  /**
    * Java API to set the values for a key
    *
    * @param v     the values as a java list
    * @param units the units of the values
    * @return a new item containing the key name, values and units
    */
  def jset(v: java.util.List[java.lang.Float], units: Units) = jset(v.asScala.toVector, units)

  /**
    * Java API: Sets the values for the key using a variable number of arguments
    *
    * @param v the values
    * @return a new item containing the key name, values and no units
    */
  @varargs
  override def jset(v: java.lang.Float*) = FloatItem(keyName, v.map(i ⇒ i: Float).toVector, units = UnitsOfMeasure.NoUnits)
}

