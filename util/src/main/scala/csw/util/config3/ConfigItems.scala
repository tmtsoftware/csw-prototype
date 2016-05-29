package csw.util.config3

import csw.util.config3.UnitsOfMeasure.{NoUnits, Units}

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable.Vector
import scala.language.implicitConversions

trait Item[+T] {
  def keyName: String

  def value: Vector[T]

  def units: Units

  def apply(index: Int): T = value(index)

  def withUnits(units: Units): Item[T]
}

abstract class Key[T](val keyName: String, val keyClass: Class[T]) extends Serializable {

  override def toString = keyName

  override def equals(that: Any): Boolean = {
    that match {
      case that: Key[T] ⇒ this.keyName == that.keyName
      case _            ⇒ false
    }
  }

  override def hashCode: Int = 41 * keyName.hashCode

  def set(v: Vector[T], units: Units): Item[T]

  @varargs def set(v: T*): Item[T]
}

object ConfigItems {

  // -- Char --

  /**
   * The type of a value for an CharKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class CharItem(keyName: String, value: Vector[Char], units: Units) extends Item[Char] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[java.lang.Character] = value.map(i ⇒ i: java.lang.Character).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.Character = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Char values
   *
   * @param nameIn the name of the key
   */
  final case class CharKey(nameIn: String) extends Key[Char](nameIn, classOf[Char]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Char], units: Units) = CharItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Char*) = CharItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[java.lang.Character], units: Units) = CharItem(keyName, v.asScala.toVector.map(i ⇒ i: Char), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Character*) = CharItem(keyName, v.map(i ⇒ i: Char).toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- Short --

  /**
   * The type of a value for an ShortKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class ShortItem(keyName: String, value: Vector[Short], units: Units) extends Item[Short] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[java.lang.Short] = value.map(i ⇒ i: java.lang.Short).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.Short = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Short values
   *
   * @param nameIn the name of the key
   */
  final case class ShortKey(nameIn: String) extends Key[Short](nameIn, classOf[Short]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Short], units: Units) = ShortItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Short*) = ShortItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[java.lang.Short], units: Units) = ShortItem(keyName, v.asScala.toVector.map(i ⇒ i: Short), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Short*) = ShortItem(keyName, v.map(i ⇒ i: Short).toVector, units = UnitsOfMeasure.NoUnits)
  }


  // -- Integer --

  /**
    * The type of a value for an IntKey
    *
    * @param keyName the name of the key
    * @param value   the value for the key
    * @param units   the units of the value
    */
  final case class IntegerItem(keyName: String, value: Vector[Integer], units: Units) extends Item[Integer] {
    /**
      * Java API
      *
      * @return the values as a java list
      */
    def jvalue: java.util.List[Integer] = value.asJava

    /**
      * Java API
      *
      * @return the value at the given index
      */
    def jvalue(index: Integer): Integer = value(index)

    /**
      * Set the units of the value
      *
      * @param unitsIn the units to set
      * @return a copy of this item with the given units set
      */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
    * A key of Integer values
    *
    * @param nameIn the name of the key
    */
  final case class IntegerKey(nameIn: String) extends Key[Integer](nameIn, classOf[Integer]) {

    /**
      * Sets the values for the key
      *
      * @param v     the values
      * @param units the units of the values
      * @return a new item containing the key name, values and units
      */
    def set(v: Vector[Integer], units: Units) = IntegerItem(keyName, v, units)

    /**
      * Sets the values for the key using a variable number of arguments
      *
      * @param v the values
      * @return a new item containing the key name, values and no units
      */
    def set(v: Integer*) = IntegerItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
      * Java API to set the values for a key
      *
      * @param v     the values as a java list
      * @param units the units of the values
      * @return a new item containing the key name, values and units
      */
    def jset(v: java.util.List[Integer], units: Units) = IntegerItem(keyName, v.asScala.toVector, units)

    /**
      * Java API: Sets the values for the key using a variable number of arguments
      *
      * @param v the values
      * @return a new item containing the key name, values and no units
      */
    @varargs
    def jset(v: java.lang.Integer*) = IntegerItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
  }



  // -- Int --

  /**
   * The type of a value for an IntKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class IntItem(keyName: String, value: Vector[Int], units: Units) extends Item[Int] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[Integer] = value.map(i ⇒ i: java.lang.Integer).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): Integer = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Int values
   *
   * @param nameIn the name of the key
   */
  final case class IntKey(nameIn: String) extends Key[Int](nameIn, classOf[Int]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Int], units: Units) = IntItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Int*) = IntItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[Integer], units: Units) = IntItem(keyName, v.asScala.toVector.map(i ⇒ i: Int), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Integer*) = IntItem(keyName, v.map(i ⇒ i: Int).toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- Long --

  /**
   * The type of a value for an LongKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class LongItem(keyName: String, value: Vector[Long], units: Units) extends Item[Long] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[java.lang.Long] = value.map(i ⇒ i: java.lang.Long).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.Long = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Long values
   *
   * @param nameIn the name of the key
   */
  final case class LongKey(nameIn: String) extends Key[Long](nameIn, classOf[Long]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Long], units: Units) = LongItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Long*) = LongItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[java.lang.Long], units: Units) = LongItem(keyName, v.asScala.toVector.map(i ⇒ i: Long), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Long*) = LongItem(keyName, v.map(i ⇒ i: Long).toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- Float --

  /**
   * The type of a value for an FloatKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class FloatItem(keyName: String, value: Vector[Float], units: Units) extends Item[Float] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[java.lang.Float] = value.map(i ⇒ i: java.lang.Float).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.Float = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Float values
   *
   * @param nameIn the name of the key
   */
  final case class FloatKey(nameIn: String) extends Key[Float](nameIn, classOf[Float]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Float], units: Units) = FloatItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Float*) = FloatItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[java.lang.Float], units: Units) = FloatItem(keyName, v.asScala.toVector.map(i ⇒ i: Float), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Float*) = FloatItem(keyName, v.map(i ⇒ i: Float).toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- Double --

  /**
   * The type of a value for an DoubleKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class DoubleItem(keyName: String, value: Vector[Double], units: Units) extends Item[Double] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[java.lang.Double] = value.map(i ⇒ i: java.lang.Double).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.Double = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Double values
   *
   * @param nameIn the name of the key
   */
  final case class DoubleKey(nameIn: String) extends Key[Double](nameIn, classOf[Double]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Double], units: Units) = DoubleItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Double*) = DoubleItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[java.lang.Double], units: Units) = DoubleItem(keyName, v.asScala.toVector.map(i ⇒ i: Double), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Double*) = DoubleItem(keyName, v.map(i ⇒ i: Double).toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- Boolean --

  /**
   * The type of a value for an BooleanKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class BooleanItem(keyName: String, value: Vector[Boolean], units: Units) extends Item[Boolean] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[java.lang.Boolean] = value.map(i ⇒ i: java.lang.Boolean).asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.Boolean = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of Boolean values
   *
   * @param nameIn the name of the key
   */
  final case class BooleanKey(nameIn: String) extends Key[Boolean](nameIn, classOf[Boolean]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[Boolean], units: Units) = BooleanItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: Boolean*) = BooleanItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[java.lang.Boolean], units: Units) = BooleanItem(keyName, v.asScala.toVector.map(i ⇒ i: Boolean), units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: java.lang.Boolean*) = BooleanItem(keyName, v.map(i ⇒ i: Boolean).toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- String --

  /**
   * The type of a value for an StringKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  final case class StringItem(keyName: String, value: Vector[String], units: Units) extends Item[String] {
    /**
     * Java API
     *
     * @return the values as a java list
     */
    def jvalue: java.util.List[String] = value.asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): java.lang.String = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)
  }

  /**
   * A key of String values
   *
   * @param nameIn the name of the key
   */
  final case class StringKey(nameIn: String) extends Key[String](nameIn, classOf[String]) {

    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[String], units: Units) = StringItem(keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: String*) = StringItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[String], units: Units) = StringItem(keyName, v.asScala.toVector, units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: String*) = StringItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
  }

  // -- Generic --

  /**
   * The type of a value for an SingleKey
   *
   * @param keyName the name of the key
   * @param value   the value for the key
   * @param units   the units of the value
   */
  case class CItem[+T](keyName: String, value: Vector[T], units: Units = NoUnits) extends Item[T] {
    //    /**
    //      * Java API
    //      *
    //      * @return the values as a java list
    //      */
    //    def jvalue: java.util.List[T] = value.asJava

    /**
     * Java API
     *
     * @return the value at the given index
     */
    def jvalue(index: Int): T = value(index)

    /**
     * Set the units of the value
     *
     * @param unitsIn the units to set
     * @return a copy of this item with the given units set
     */
    def withUnits(unitsIn: Units) = copy(units = unitsIn)

    override def toString = s"$keyName$units($value)"
  }

  /**
   * A key for values of the generic type A.
   *
   * @param kn the name of the key
   * @param kc the class of the key value's data type (The actual value will be a Vector[A])
   */
  case class SingleKey[A](kn: String, kc: Class[A]) extends Key[A](kn, kc) {
    /**
     * Sets the values for the key
     *
     * @param v     the values
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def set(v: Vector[A], units: Units) = CItem[A](keyName, v, units)

    /**
     * Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    def set(v: A*) = CItem[A](keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

    /**
     * Java API to set the values for a key
     *
     * @param v     the values as a java list
     * @param units the units of the values
     * @return a new item containing the key name, values and units
     */
    def jset(v: java.util.List[A], units: Units) = CItem[A](keyName, v.asScala.toVector, units)

    /**
     * Java API: Sets the values for the key using a variable number of arguments
     *
     * @param v the values
     * @return a new item containing the key name, values and no units
     */
    @varargs
    def jset(v: A*) = CItem[A](keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
  }

  trait EnumValue {
    def value: String

    def name: String

    def description: String
  }

  case class OneEnum(name: String, value: String, description: String = "") extends EnumValue

  case class EnumKey(nameIn: String, possibles: Vector[EnumValue]) extends Key[EnumValue](nameIn, classOf[EnumValue]) {
    //private def doSet(v: Vector(EnumValue))
    def set(v: Vector[EnumValue], units: Units) = CItem[EnumValue](keyName, v, units)

    def set(v: EnumValue*) = CItem(keyName, v.toVector, units = UnitsOfMeasure.NoUnits)
  }

}