package csw.util.cfg

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

  object Deg extends Units("deg")

}

object FullyQualifiedName {
  val SEPARATOR = '.'

  case class Fqn(fqn: String) {
    assert(fqn != null, "fqn can not be a null string")

    lazy val prefix = Fqn.prefix(fqn)
    lazy val name = Fqn.name(fqn)
  }

  object Fqn {
    implicit def strToFqn(s: String) = Fqn(s)

    private def getPrefixName(s: String): (String, String) = {
      val result = s.splitAt(s.lastIndexOf(SEPARATOR))
      // this skips over the SEPARATOR, if present
      if (isFqn(s)) (result._1, result._2.substring(1)) else result
    }

    def isFqn(s: String): Boolean = s.contains(SEPARATOR)

    def prefix(s: String): String = s.splitAt(s.lastIndexOf(SEPARATOR))._1

    def subsystem(s: String): String = s.splitAt(s.indexOf(SEPARATOR))._1

    // Split doesn't remove the separator, so skip it
    def name(fqn: String): String = getPrefixName(fqn)._2

    // Attempt to think about what to do for improper name
    // Currently not in use since I can't manage to do it functionally with Some, etc
    // XXX allan: use Try?
    def validateName(trialName: String): Option[String] = {
      if (trialName.isEmpty) None
      else if (trialName.contains(SEPARATOR)) None
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
  case class ValueData[+A](elems: Seq[A], units: Units = NoUnits) extends AllValues[A] {
    def :+[B >: A](elem: B) = ValueData(elems ++ Seq(elem), units)

    // DSL support for units
    def deg = ValueData(elems, Deg)

    //...

    override def toString = elems.mkString("(", ", ", ")") + units
  }

  object ValueData {
    def empty[A]: ValueData[A] = ValueData(Seq.empty, NoUnits)

    def withUnits[A](v1: ValueData[A], u: Units) = ValueData(v1.elems, u)

    def withValues[A](v1: ValueData[A], values: Seq[A]) = ValueData(values, v1.units)

    // DSL support XXX
    implicit def toValueData[A](elem: A) = ValueData(Seq(elem))
  }


  /**
   * A CValue is a configuration value. This joins a fully qualified name (future object?)
   * with ValueData
   */
  case class CValue[+A](private val trialName: String, data: ValueData[A] = ValueData.empty) {

    import FullyQualifiedName.Fqn

    //The following bit is to auto take off the name from an FQN
    lazy val name = Fqn.name(trialName)

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

    implicit def tupleToCValue[A](t: (String, A)): CValue[A] = CValue[A](t._1, ValueData[A](Seq(t._2), NoUnits))

  }

}


object Configurations {

  import ConfigValues.CValue

  // Base trait for all Configuration Types
  trait ConfigType {
    def obsId: String
  }

  type CV = CValue[_]
  type CT = ConfigType

  // obsId might be changed to some set of observation Info type
  case class SetupConfig(obsId: String,
                         prefix: String = SetupConfig.DEFAULT_PREFIX,
                         values: Set[CV] = Set.empty[CV]) extends ConfigType {

    lazy val size = values.size

    lazy val names: Set[String] = values.map(c => c.name)

    lazy val empty = values.empty

    lazy val notEmpty = values.nonEmpty

    def :+[B <: CV](elem: B): SetupConfig = {
      SetupConfig(obsId, prefix, values + elem)
    }

    def withValues(newValues: CV*): SetupConfig = {
      SetupConfig(obsId, prefix, values ++ newValues)
    }

    // Needs improvement (XXX use JSON output)
    override def toString = "(" + obsId + ")->" + prefix + " " + values
  }

  object SetupConfig {
    val DEFAULT_PREFIX = ""

    def apply(obsId: String, prefix: String, values: CV*): SetupConfig = SetupConfig(obsId, prefix, values.toSet)
  }

  case class WaitConfig(obsId: String) extends ConfigType

  case class ObserveConfig(obsId: String) extends ConfigType


  type ConfigList[A <: ConfigType] = List[A]

  implicit def listToConfigList[A <: ConfigType](l: List[A]): ConfigListWrapper[A] = ConfigListWrapper(l)

  implicit def listToSetupConfigList[A <: SetupConfig](l: List[A]): SetupConfigListWrapper[A] = SetupConfigListWrapper(l)


  object ConfigList {

    // A filter type for various kinds of Configs
    type ConfigFilter[A] = A => Boolean

    val startsWithFilter: String => ConfigFilter[SetupConfig] = query => sc => sc.prefix.startsWith(query)

    val containsFilter: String => ConfigFilter[SetupConfig] = query => sc => sc.prefix.contains(query)

    def apply(configs: ConfigType*) = new ConfigListWrapper(configs.toList)
  }


  case class SetupConfigListWrapper[A <: SetupConfig](configs: List[A]) {

    import ConfigList._

    def select(f: ConfigFilter[SetupConfig]): List[SetupConfig] = configs.filter(f)

  }

  case class ConfigListWrapper[A <: ConfigType](configs: List[A]) {

    import ConfigList._

    lazy val prefixes: Set[String] = onlySetupConfigs.map(c => c.prefix).toSet

    lazy val obsIds: Set[String] = configs.map(sc => sc.obsId).toSet

    lazy val onlySetupConfigs: List[SetupConfig] = configs.collect { case sc: SetupConfig => sc}

    lazy val onlyWaitConfigs: List[WaitConfig] = configs.collect { case sc: WaitConfig => sc}

    lazy val onlyObserveConfigs: List[ObserveConfig] = configs.collect { case sc: ObserveConfig => sc}

    private def select(f: ConfigFilter[SetupConfig]): List[SetupConfig] = onlySetupConfigs.select(f)

    def startsWith(query: String): List[SetupConfig] = select(startsWithFilter(query))

    def contains(query: String): List[SetupConfig] = select(containsFilter(query))

    lazy val getFirst: List[SetupConfig] = {
      val scList = onlySetupConfigs
      scList.select(startsWithFilter(scList.head.prefix))
    }
  }

}

object TmpMain {
  // Temporary test main for experimenting...
  def main(args: Array[String]) {
    import Configurations.SetupConfig
    import ConfigValues.ValueData._

    val sc = SetupConfig(
      obsId = "2014-C2-4-44",
      "tcs.base.pos",
      "name" -> "m99",
      "ra" -> (10.2 deg),
      "dec" -> 2.34.deg,
      "equinox" -> "J2000"
    )

    println(s"sc = $sc")
  }
}


