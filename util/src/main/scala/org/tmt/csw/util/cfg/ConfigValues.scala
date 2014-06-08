package org.tmt.csw.util.cfg

import scala.collection._

/**
 * This Units stuff is just for play
 * although something should be developed or borrowed
 * for use.
 */
object UnitsOfMeasure {

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

    lazy val prefix = Fqn.prefix(fqn)
    lazy val name = Fqn.name(fqn)
  }

  object Fqn {
    implicit def strToFqn(s: String) = Fqn(s)

    //def apply(fqn:String) = new Fqn(fqn)
    private def getPrefixName(s: String): (String, String) = {
      if (isFqn(s)) {
        val result = s.splitAt(s.lastIndexOf(SEPERATOR))
        // this skips over the SEPERATOR
        (result._1, result._2.substring(1))
      } else {
        ("", s)
      }
    }

    def isFqn(s: String): Boolean = s.contains(SEPERATOR)

    def prefix(fqn: String): String = {
      val r = getPrefixName(fqn)
      r._1
    }

    def subsystem(fqn: String): String = {
      // Get the prefix for the fqn, if it's empty, return
      val prefix = Fqn.prefix(fqn)
      if (prefix.isEmpty) prefix
      else if (prefix.contains(SEPERATOR)) {
        // Else check to see if prefix still contains a prefix
        // if so, get the text before the first Seperator
        prefix.splitAt(prefix.indexOf(SEPERATOR))._1
      } else prefix
    }

    def name(fqn: String): String = {
      // Split doesn't remove the seperator, so skip it
      val r = getPrefixName(fqn)
      r._2
    }

    // Attempt to think about what to do for improper name
    // Currently not in use since I can't manage to do it functionally with Some, etc
    // XXX allan: use Try?
    def validateName(trialName: String): Option[String] = {
      if (trialName.isEmpty) None
      else if (trialName.contains(SEPERATOR)) None
      else Some(trialName)
    }
  }

}

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
  class ValueData[+A](val elems: Seq[A], val units: Units = NoUnits) extends AllValues[A] {

    def :+[B >: A](elem: B) = ValueData(elems ++ Seq(elem), units)

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
  case class CValue[+A](private val trialName: String, data: ValueData[A]) {

    import org.tmt.csw.util.cfg.FullyQualifiedName.Fqn

    //The following bit is to auto take off the name from an FQN
    val name = Fqn.name(trialName)

    def apply(idx: Int) = data(idx)

    def length = data.elems.length

    def isEmpty = data.elems.isEmpty

    def elems = data.elems

    def units = data.units

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

    def apply[A](name: String): CValue[A] = CValue[A](name, ValueData.empty)
  }

}


object Configurations {

  import ConfigValues.CValue

  // This really sucks but I can't figure out how to get only subtypes of ConfigType because of type erasure
  object ConfigKind extends Enumeration {
    type ConfigKind = Value
    val Setup, Observe, Wait = Value
  }

  // Base trait for all Configuration Types
  trait ConfigType {
    import org.tmt.csw.util.cfg.Configurations.ConfigKind.ConfigKind
    def obsId: String

    def kind: ConfigKind
  }

  type CV = CValue[_]
  type CT = ConfigType

  // obsId might be changed to some set of observation Info type
  case class SetupConfig(obsId: String, prefix: String, values: Set[CV]) extends ConfigType {

    val kind = ConfigKind.Setup

    def size = values.size

    def names: Set[String] = values.map(c => c.name)

    def empty = values.empty

    def notEmpty = values.nonEmpty

    def :+[B <: CV](elem: B): SetupConfig = {
      SetupConfig(obsId, prefix, values + elem)
    }

    def withValues(newValues: CV*): SetupConfig = {
      SetupConfig(obsId, prefix, values ++ newValues)
    }

    // Needs improvement
    override def toString = "(" + obsId + ")->" + prefix + " " + values
  }

  object SetupConfig {
    val DEFAULT_PREFIX = ""

    def apply(obsId: String, prefix: String = DEFAULT_PREFIX) = new SetupConfig(obsId, prefix, Set.empty[CV])
  }

  case class WaitConfig(obsId: String) extends ConfigType {
    val kind = ConfigKind.Wait

  }

  case class ObserveConfig(obsId: String) extends ConfigType {
    val kind = ConfigKind.Observe
  }


  case class ConfigList[A <: ConfigType](configs: List[A]) {

    def size = configs.size

    def :+[B <: CT](elem: B): ConfigList[CT] = new ConfigList(configs.+:(elem))

    def prefixes: Set[String] = ConfigList.onlySetupConfigs(configs).map(c => c.prefix).toSet

    def obsIds: Set[String] = configs.map(sc => sc.obsId).toSet

    def map[B](f: CT => B) = configs.map(f)

    def flatMap[B](f: A => GenTraversable[B]) = configs.flatMap(f)

    def foreach[U](f: A => U): Unit = configs.foreach(f)

    def filter(f: A => Boolean): Seq[A] = configs.filter(f)
  }


  object ConfigList {

    // A filter type for various kinds of Configs
    type ConfigFilter[A] = A => Boolean

    private def selectOnSetupConfigs(configs: List[SetupConfig], f: ConfigFilter[SetupConfig]): List[SetupConfig]
      = configs.filter(f)

    def onlySetupConfigs(configs: List[ConfigType]): List[SetupConfig]
      = configs.filter(c => c.kind == ConfigKind.Setup).asInstanceOf[List[SetupConfig]]

    def onlyWaitConfigs(configs: List[ConfigType]): List[WaitConfig]
      = configs.filter(c => c.kind == ConfigKind.Wait).asInstanceOf[List[WaitConfig]]

    def onlyObserveConfigs(configs: List[ConfigType]): List[ObserveConfig]
      = configs.filter(c => c.kind == ConfigKind.Observe).asInstanceOf[List[ObserveConfig]]

    private def select(configs: List[ConfigType], f: ConfigFilter[SetupConfig]): List[SetupConfig]
      = selectOnSetupConfigs(onlySetupConfigs(configs), f)

    private val startsWithFilter: String => ConfigFilter[SetupConfig]
      = query => sc => sc.prefix.startsWith(query)
    private val containsFilter: String => ConfigFilter[SetupConfig]
      = query => sc => sc.prefix.contains(query)

    def startsWith(cl: ConfigList[ConfigType], query: String): List[SetupConfig]
      = select(cl.configs, startsWithFilter(query))

    def contains(cl: ConfigList[ConfigType], query: String): List[SetupConfig]
      = select(cl.configs, containsFilter(query))

    def getFirst(values: List[ConfigType]): List[SetupConfig] = {
      val scList = onlySetupConfigs(values)
      select(scList, startsWithFilter(scList.head.prefix))
    }

    def apply(configs: ConfigType*) = new ConfigList(configs.toList)
  }


}

