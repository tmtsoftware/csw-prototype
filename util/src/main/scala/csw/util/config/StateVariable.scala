package csw.util.config

import csw.util.config.Configurations._
import csw.util.config.UnitsOfMeasure.Units

import scala.language.implicitConversions
import scala.annotation.varargs
import java.util.Optional
import scala.collection.JavaConverters._

/**
 * Base trait for state variables
 */
sealed trait StateVariable

object StateVariable {

  /**
   * Type of a function that returns true if two state variables (demand and current)
   * match (or are close enough, which is implementation dependent)
   */
  type Matcher = (DemandState, CurrentState) ⇒ Boolean

  /**
   * The default matcher for state variables tests for an exact match
   *
   * @param demand  the demand state
   * @param current the current state
   * @return true if the demand and current states match (in this case, are equal)
   */
  def defaultMatcher(demand: DemandState, current: CurrentState): Boolean =
    demand.prefix == current.prefix && demand.items == current.items

  /**
   * A state variable that indicates the ''demand'' or requested state.
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class DemandState(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]])
      extends ConfigType[DemandState] with StateVariable {

    override def create(data: ConfigData) = DemandState(configKey, data)

    /**
     * This is here for Java to construct with String
     */
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    /**
     * Java API to create a DemandState from a SetupConfig
     */
    def this(config: SetupConfig) = this(config.prefix, config.items)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): DemandState = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): DemandState = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): DemandState = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): DemandState = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): DemandState = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): DemandState = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): DemandState = super.remove[S, J](key)
  }

  object DemandState {
    /**
     * Converts a SetupConfig to a DemandState
     */
    implicit def apply(config: SetupConfig): DemandState = DemandState(config.prefix, config.items)
  }

  /**
   * A state variable that indicates the ''current'' or actual state.
   *
   * @param configKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class CurrentState(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_, _]])
      extends ConfigType[CurrentState] with StateVariable {

    override def create(data: ConfigData) = CurrentState(configKey, data)

    /**
     * This is here for Java to construct with String
     */
    def this(configKey: String) = this(ConfigKey.stringToConfigKey(configKey))

    /**
     * Java API to create a DemandState from a SetupConfig
     */
    def this(config: SetupConfig) = this(config.prefix, config.items)

    // The following overrides are needed for the Java API and javadocs
    // (Using a Java interface caused various Java compiler errors)
    override def add[S, J](item: Item[S, J]): CurrentState = super.add(item)

    override def set[S, J](key: Key[S, J], units: Units, v: S*): CurrentState = super.set[S, J](key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], units: Units, v: J*): CurrentState = super.jset(key, units, v: _*)

    @varargs
    override def jset[S, J](key: Key[S, J], v: J*): CurrentState = super.jset(key, v: _*)

    override def jset[S, J](key: Key[S, J], units: Units, v: java.util.List[J]): CurrentState = super.jset(key, units, v)

    override def jset[S, J](key: Key[S, J], v: java.util.List[J]): CurrentState = super.jset(key, v)

    override def jvalue[S, J](key: Key[S, J], index: Int): J = super.jvalue(key, index)

    override def jvalue[S, J](key: Key[S, J]): J = super.jvalue(key)

    override def jvalues[S, J](key: Key[S, J]): java.util.List[J] = super.jvalues(key)

    override def jget[S, J](key: Key[S, J]): Optional[Item[S, J]] = super.jget(key)

    override def jget[S, J](key: Key[S, J], index: Int): Optional[J] = super.jget(key, index)

    override def remove[S, J](key: Key[S, J]): CurrentState = super.remove[S, J](key)
  }

  object CurrentState {
    /**
     * Converts a SetupConfig to a CurrentState
     */
    implicit def apply(config: SetupConfig): CurrentState = CurrentState(config.prefix, config.items)

    /**
     * Java API to create a CurrentState from a SetupConfig
     */
    def fromSetupConfig(config: SetupConfig): CurrentState = CurrentState(config.prefix, config.items)
  }


  /**
    * Combines multiple CurrentState objects together
    *
    * @param states one or more CurrentStates
    */
  final case class CurrentStates(states: CurrentState*) {
    /**
      * Java API: Returns the list of CurrentState objects
      */
    def jstates: java.util.List[CurrentState] = states.asJava
  }
}
