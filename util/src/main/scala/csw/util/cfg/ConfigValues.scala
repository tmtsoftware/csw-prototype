package csw.util.cfg
import scala.language.implicitConversions

/**
 * Defines configuration values (the dynamic contents of a configuration, as opposed to the fixed fields).
 */
object ConfigValues {

  import UnitsOfMeasure._

  /**
   * Trying here to represent values separately so we might be able to handle other non-numeric kinds
   * @tparam A Type of contained value
   */
  trait AllValues[+A] {
    /**
     * @return the entire sequence
     */
    def elems: Seq[A]

    /**
     * Returns an individual value
     * @param idx index
     * @return value of type A
     */
    def apply(idx: Int): A = elems(idx)
  }

  /**
   * This class groups a sequence of values and units
   * We may add other value information in the future?
   * @param elems a sequence of type A
   * @param units units of the values
   * @tparam A Type of contained value
   */
  case class ValueData[+A](elems: Seq[A], units: Units = NoUnits) extends AllValues[A] {
    def :+[B >: A](elem: B) = ValueData(elems ++ Seq(elem), units)

    // DSL support for units
    def deg = ValueData(elems, Deg)

    def meters = ValueData(elems, Meters)

    def meter = ValueData(elems, Meters)

    def millisecond = ValueData(elems, Milliseconds)

    def milliseconds = ValueData(elems, Milliseconds)

    def ms = ValueData(elems, Milliseconds)

    def second = ValueData(elems, Seconds)

    def seconds = ValueData(elems, Seconds)

    def sec = ValueData(elems, Seconds)

    //...

    override def toString = elems.mkString("(", ", ", ")") + units
  }

  object ValueData {
    def empty[A]: ValueData[A] = ValueData(Seq.empty, NoUnits)

    def withUnits[A](v1: ValueData[A], u: Units) = ValueData(v1.elems, u)

    def withValues[A](v1: ValueData[A], values: Seq[A]) = ValueData(values, v1.units)

    // DSL support (XXX deal with duration implicit defs)
    implicit def toValueData[A](elem: A) = elem match {
      case s: Seq[A @unchecked] ⇒ ValueData(s)
      //      case p: Product => ValueData(p.productIterator.toList)
      case _                    ⇒ ValueData(Seq(elem))
    }
  }

  /**
   * A CValue is a configuration value. This joins a fully qualified name (future object?)
   * with ValueData
   */
  case class CValue[+A](trialName: String, data: ValueData[A] = ValueData.empty) {

    val name = trialName

    def apply(idx: Int) = data(idx)

    lazy val length = data.elems.length

    lazy val isEmpty = data.elems.isEmpty

    lazy val elems = data.elems

    lazy val units = data.units

    // Should we have a way to add an element of type A to the data?
    def :+[B >: A](elem: B): CValue[B] = new CValue(name, data :+ elem)

    override def toString = name + data
  }

  object CValue {
    /**
     * Allows creating a CValue with a sequence or values as a vararg
     * @param name final name in a fully qualified name as az in "tcs.m1cs.az"
     * @param units units for values (unfortunately cannot be defaulted with vararg
     * @param data values of type A
     * @tparam A type of values
     * @return a new CValue instance
     */
    def apply[A](name: String, units: Units, data: A*): CValue[A] = CValue[A](name, ValueData[A](data, units))

    implicit def tupleToCValue[A](t: (String, A)): CValue[A] = t._2 match {
      case v: ValueData[A @unchecked] ⇒
        CValue[A](t._1, v)
      case s: Seq[A @unchecked] ⇒
        CValue[A](t._1, ValueData[A](s, NoUnits))
      //      case p: Product =>
      //        CValue[A](t._1, ValueData[A](p.productIterator.toSeq, NoUnits))
      case _ ⇒
        CValue[A](t._1, ValueData[A](Seq(t._2), NoUnits))
    }
  }
}

