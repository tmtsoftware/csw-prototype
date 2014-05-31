package org.tmt.csw.util.cfg

/**
 * This Units stuff is just for play
 * although something should be developed or borrowed
 * for use.
 */
object Units {

  // Should parameterize Units so concat can be created concat[A, B]
  case class Units(name: String) {
    override def toString = "[" + name + "]"
  }

  object NoUnits extends Units("none")

  object Meters extends Units("m")

}

object ConfigValues {

  import Units._

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
   * This class groups a sequence of values and units.
   * We may add other value information in the future?
   * @param elems a sequence of type A
   * @param units units of the values
   * @tparam A Type of contained value
   */
  case class ValueData[+A](elems: Seq[A], units: Units = NoUnits) extends AllValues[A] {
    def :+[B >: A](elem: B) = ValueData(elems :+ elem, units)

    override def toString = elems.mkString("(", ", ", ")") + units
  }

  object ValueData {
    def empty[A]: ValueData[A] = new ValueData(Seq.empty, NoUnits)

    def withUnits[A](v1: ValueData[A], u: Units) = ValueData(v1.elems, u)

    def withValues[A](v1: ValueData[A], values: Seq[A]) = ValueData(values, v1.units)
  }


  /**
   * A CValue is a configuration value. This joins a fully qualified name (future object?)
   * with ValueData
   * @param fqn fully qualified name of value
   * @param data the value data
   */
  case class CValue[+A](fqn: String, data: ValueData[A]) {
    def apply(idx: Int) = data(idx)

    def length = data.elems.length

    def isEmpty = data.elems.isEmpty

    def elems = data.elems

    def units = data.units

    // Should we have a way to add an element of type A to the data?
    def :+[B >: A](elem: B): CValue[B] = CValue(fqn, data :+ elem)

    override def toString = fqn + data
  }

  object CValue {
    /**
     * Allows creating a CValue with a sequence or values as a vararg
     * @param fqn fully qualified name as in "tcs.m1cs.az
     * @param units units for values (unfortunately cannot be defaulted with vararg)
     * @param data values of type A
     * @tparam A type of values
     * @return a new CValue instance
     */
    def apply[A](fqn: String, units: Units, data: A*): CValue[A] = CValue[A](fqn, ValueData[A](data, units))

    def apply[A](fqn: String): CValue[A] = CValue[A](fqn, ValueData.empty)
  }

}

import ConfigValues._
import Units._

case class SetupConfig(obsId: String, values: CValue[_]*) {
  def apply(idx: Int) = values(idx)

  def :+(value: CValue[_]): SetupConfig = SetupConfig(obsId, values :+ value: _*)

  override def toString = values.mkString(s"SetupConfig($obsId, ", ", ", ")")
}


object SetupConfigTest {

  // XXX temp
  def main(args: Array[String]) {
    val t = SetupConfig("obs100", CValue("mobie.red.filter", NoUnits, "F1-red"))
    val t2 = t :+ CValue("tcs.base.pos.name", NoUnits, "m59")
    val t3 = t2 :+ CValue("tcs.base.pos.equinox", NoUnits, 2000)

    println(s"t.class = ${t.values.getClass}")
    println(s"t = $t")
    println(s"t2 = $t2")
    println(s"t3 = $t3")
    println(s"t(0) = ${t(0)}")
    println(s"t3(2) = ${t3(2)}")

//    val x = t(0).elems
    // Any...
  }
}


