package csw.util.config

import csw.util.config.UnitsOfMeasure.Units

import scala.reflect.ClassTag

/**
  *
  * Scala Key classes
  *
  */
case class ShortKey(nameIn: String, unitsIn: Units) extends Key1[Short](nameIn, unitsIn) {
  def set(v: Short) = CItem(this, v)
}
case class IntKey(nameIn: String, unitsIn: Units) extends Key1[Int](nameIn, unitsIn) {
  def set(v: Int) = CItem(this, v)
}
case class LongKey(nameIn: String, unitsIn: Units) extends Key1[Long](nameIn, unitsIn) {
  def set(v: Long) = CItem(this, v)
}
case class FloatKey(nameIn: String, unitsIn: Units) extends Key1[Float](nameIn, unitsIn) {
  def set(v: Float) = CItem(this, v)
}
case class DoubleKey(nameIn: String, unitsIn: Units) extends Key1[Double](nameIn, unitsIn) {
  def set(v: Double) = CItem(this, v)
}
case class BooleanKey(nameIn: String, unitsIn: Units) extends Key1[Boolean](nameIn, unitsIn) {
  def set(v: Boolean) = CItem(this, v)
}


case class JShortKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Short](nameIn, unitsIn) {
  def set(v: java.lang.Short) = CItem(this, v)
}
case class JIntKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Integer](nameIn, unitsIn) {
  def set(v: java.lang.Integer) = CItem[java.lang.Integer](this, v)
}
case class JLongKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Long](nameIn, unitsIn) {
  def set(v: java.lang.Long) = CItem(this, v)
}
case class JFloatKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Float](nameIn, unitsIn) {
  def set(v: java.lang.Float) = CItem(this, v)
}
case class JDoubleKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Double](nameIn, unitsIn) {
  def set(v: java.lang.Double) = CItem[java.lang.Double](this, v)
}
case class JBooleanKey(nameIn: String, unitsIn: Units) extends Key1[java.lang.Boolean](nameIn, unitsIn) {
  def set(v: java.lang.Boolean) = CItem(this, v)
}

case class StringKey(nameIn: String, unitsIn: Units) extends Key1[String](nameIn, unitsIn) {
  def set(v: String) = CItem[String](this, v)
}

class SingleKey[A](nameIn: String, unitsIn: Units) extends Key1[A](nameIn, unitsIn) {
  def set(v: A) = CItem[A](this, v)
}


/**
 * Key for an array of values of type A in the given units.
 */
case class ArrayKey[A](nameIn: String, unitsIn: Units) extends Key1[Seq[A]](nameIn, unitsIn) {
  def set(v: Seq[A]) = CItem[Seq[A]](this, v)

  /**
   * Allows setting the value from Scala with a variable number of arguments
   */
  def set[X: ClassTag](v: A*) = CItem(this, v.toSeq)

  /**
   * Java varargs API: allows setting one or more values from Java
   */
  @annotation.varargs
  def jset(v: A*) = CItem(this, v.toSeq)
}

/**
  * A key that has an Int array as a value
  */
class IntArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[Int](nameIn, unitsIn)
object IntArrayKey {
  def apply(nameIn: String, unitsIn:Units): IntArrayKey = new IntArrayKey(nameIn, unitsIn)
}

/**
  * A key that has a java Integer array as a value
  */
class JIntArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[java.lang.Integer](nameIn, unitsIn)
object JIntArrayKey {
  def apply(nameIn: String, unitsIn:Units): JIntArrayKey = new JIntArrayKey(nameIn, unitsIn)
}

/**
  * A key that has a Double array as a value
  */
class DoubleArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[Double](nameIn, unitsIn)
object DoubleArrayKey {
  def apply(nameIn: String, unitsIn:Units): DoubleArrayKey = new DoubleArrayKey(nameIn, unitsIn)
}

/**
  * A key that has a java Double array as a value
  */
class JDoubleArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[java.lang.Double](nameIn, unitsIn)
object JDoubleArrayKey {
  def apply(nameIn: String, unitsIn:Units): JDoubleArrayKey = new JDoubleArrayKey(nameIn, unitsIn)
}

/**
  * A key that has a Short array as a value
  */
class ShortArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[Short](nameIn, unitsIn)
object ShortArrayKey {
  def apply(nameIn: String, unitsIn:Units): ShortArrayKey = new ShortArrayKey(nameIn, unitsIn)
}

/**
  * A key that has a java Short array as a value
  */
class JShortArrayKey(nameIn: String, unitsIn:Units) extends ArrayKey[java.lang.Short](nameIn, unitsIn)
object JShortArrayKey {
  def apply(nameIn: String, unitsIn:Units): JShortArrayKey = new JShortArrayKey(nameIn, unitsIn)
}


/**
  * Base type of an enum value
  */
trait eValue {
  def value: String

  def name: String

  def description: String
}

case class enumValue(name: String, value: String, description: String = "") extends eValue

case class EnumKey(nameIn: String, unitsIn: Units, possibles: Seq[eValue]) extends Key1[eValue](nameIn, unitsIn) {
  def set(v: eValue) = CItem[eValue](this, v)
}


