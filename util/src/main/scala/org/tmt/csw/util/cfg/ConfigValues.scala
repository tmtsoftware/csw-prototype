package org.tmt.csw.util.cfg

import scala.collection._

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

object FullyQualifiedName {
  val SEPERATOR = '.'

  case class Fqn(fqn: String) {
    assert(fqn != null, "fqn can not be a null string")

    val prefix = Fqn.prefix(this.fqn)
    val name = Fqn.name(this.fqn)
  }

  object Fqn {
    //def apply(fqn:String) = new Fqn(fqn)
    private def getPrefixName(s: String): (String, String) = {
      s.splitAt(s.lastIndexOf(SEPERATOR))
    }

    def prefix(fqn: String): String = {
      getPrefixName(fqn)._1
    }

    def name(fqn: String): String = {
      // Split doesn't remove the seperator, so skip it
      getPrefixName(fqn)._2.substring(1)
    }
  }

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
   * This class groups a sequence of values and units
   * We may add other value information in the future?
   * @param elems a sequence of type A
   * @param units units of the values
   * @tparam A Type of contained value
   */
  class ValueData[+A](val elems: Seq[A], val units: Units = NoUnits) extends AllValues[A] {

    def :+[B >: A](elem: B) = ValueData.apply(elems ++ Seq(elem), units)

    override def toString = elems.mkString("(", ", ", ")") + units
  }

  object ValueData {
    /**
     * Convenience to create ValueData easily
     * @param values sequence of values
     * @param units units
     * @tparam A type of sequence values
     * @return a new ValueData object
     */
    def apply[A](values: Seq[A], units: Units = NoUnits) = new ValueData(values, units)

    def empty[A]: ValueData[A] = new ValueData(Seq.empty, NoUnits)

    def withUnits[A](v1: ValueData[A], u: Units) = ValueData(v1.elems, u)

    def withValues[A](v1: ValueData[A], values: Seq[A]) = ValueData(values, v1.units)
  }


  /**
   * A CValue is a configuration value. This joins a fully qualified name (future object?)
   * with ValueData
   */
  case class CValue[+A](fqn: String, data: ValueData[A]) {
    def apply(idx: Int) = data.apply(idx)

    def length = data.elems.length

    def isEmpty = data.elems.isEmpty

    def elems = data.elems

    def units = data.units

    // Should we have a way to add an element of type A to the data?
    def :+[B >: A](elem: B): CValue[B] = new CValue(fqn, data :+ elem)

    override def toString = fqn + data
  }

  object CValue {
    /**
     * Allows creating a CValue with a sequence or values as a vararg
     * @param fqn fully qualified name as in "tcs.m1cs.az
     * @param units units for values (unfortunately cannot be defaulted with vararg
     * @param data values of type A
     * @tparam A type of values
     * @return a new CValue instance
     */
    def apply[A](fqn: String, units: Units, data: A*): CValue[A] = new CValue[A](fqn, ValueData[A](data, units))

    def apply[A](fqn: String): CValue[A] = new CValue[A](fqn, ValueData.empty)
  }

}


object Config {

  import ConfigValues.CValue

  type A = CValue[_]

  // obsId might be changed to some set of observation Info type
  case class SetupConfig(obsId: String, var values: Set[A]) {

    def size = values.size

    def empty = values.empty

    def notEmpty = values.nonEmpty

    def map[B](f: A => B) = values.map(f)

    def flatMap[B](f: A => GenTraversable[B]) = values.flatMap(f)

    def foreach[U](f: A => U): Unit = values.foreach(f)

    def filter(f: A => Boolean): Set[A] = values.filter(f)

    def :+[B <: A](elem: B): SetupConfig = {
      //new SetupConfig(obsId, values :+ elem)
      values = values + elem
      this
    }

    def withValues(newValues: A*): SetupConfig = {
      newValues.foreach { v => values = values + v}
      this
    }

    override def toString = "(" + obsId + ")->" + values
  }

  object SetupConfig {

    import FullyQualifiedName._

    def apply(obsId: String) = new SetupConfig(obsId, Set.empty[A])

    def fqns(sc: SetupConfig): Set[String] = sc.values.map(c => c.fqn)

    type CValueFilter = CValue[_] => Boolean

    val startsWithFilter: String => CValueFilter = query => cv => cv.fqn.startsWith(query)
    val containsFilter: String => CValueFilter = query => cv => cv.fqn.contains(query)

    private def select(values: Set[A], f: CValueFilter): Set[A] = values.filter(f)

    def startsWith(sc: SetupConfig, query: String): Set[A] = select(sc.values, startsWithFilter(query))

    def contains(sc: SetupConfig, query: String): Set[A] = select(sc.values, containsFilter(query))

    def getFirst(values: Set[A]): Set[A] = {
      val q = Fqn.prefix(values.head.fqn)
      select(values, startsWithFilter(q))
    }

    def getFirst(sc: SetupConfig): Set[A] = {
      getFirst(sc.values)
    }

    def getConfig(sc: SetupConfig) = {
      sc.values.partition(cv => Fqn.prefix(cv.fqn) == Fqn.prefix(sc.values.head.fqn))
    }

    // Returns a list of configs in list
    def getAllConfigs(sc: SetupConfig): List[SetupConfig] = {

      def getAllHelper(values: Set[A], current_result: List[SetupConfig]): List[SetupConfig] = {
        if (values.isEmpty) current_result
        else {
          val first: Set[A] = getFirst(values)
          val next_result = SetupConfig(sc.obsId, first) +: current_result
          val next_values = values -- first
          getAllHelper(next_values, next_result)
        }
      }

      getAllHelper(sc.values, List.empty[SetupConfig])
    }
  }

}


//object SetupConfigTest {
//
//  // XXX temp
//  def main(args: Array[String]) {
//    val t = SetupConfig("obs100", CValue("mobie.red.filter", NoUnits, "F1-red"))
//    val t2 = t :+ CValue("tcs.base.pos.name", NoUnits, "m59")
//    val t3 = t2 :+ CValue("tcs.base.pos.equinox", NoUnits, 2000)
//
//    println(s"t.class = ${t.values.getClass}")
//    println(s"t = $t")
//    println(s"t2 = $t2")
//    println(s"t3 = $t3")
//    println(s"t(0) = ${t(0)}")
//    println(s"t3(2) = ${t3(2)}")
//
////    val x = t(0).elems
//    // Any...
//  }
//}


