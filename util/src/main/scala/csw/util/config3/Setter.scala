package csw.util.config3

import csw.util.config3.ConfigItems.CItem
import csw.util.config3.UnitsOfMeasure.Units

/**
 * TMT Source Code: 5/15/16.
 * This is just test code to look at making the Key interface a type class for different value types.
 */
object Setter {
  /**
   * Typeclass to serialize
   */
  trait Setter[A] {
    def set(key: Key[A], units: Units, value: Vector[A]): CItem[A]
  }

  def set[A](key: Key[A], units: Units, value: Vector[A])(implicit st: Setter[A]): CItem[A] = st.set(key, units, value)

  implicit object SetIntSerializer extends Setter[Int] {
    def set(key: Key[Int], units: Units, value: Vector[Int]) = CItem[Int](key.keyName, value, units)
  }

}
