package csw.util.config

import csw.util.config.Configurations._

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.language.implicitConversions

object StateVariable {

  /**
   * Base trait for state variables
   */
  sealed trait StateVariable extends Serializable

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
  case class DemandState(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]])
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
  case class CurrentState(configKey: ConfigKey, items: ConfigData = Set.empty[Item[_]])
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
  final case class CurrentStates(states: Seq[CurrentState]) {

    /**
     * Java API: Returns the list of CurrentState objects
     */
    def jstates: java.util.List[CurrentState] = states.asJava
  }

  /**
   * For the Java API
   *
   * @param states one or more CurrentState objects
   * @return a new CurrentStates object containing all the given CurrentState objects
   */
  @varargs
  def createCurrentStates(states: CurrentState*): CurrentStates = CurrentStates(states)

  /**
   * For the Java API
   *
   * @param states one or more CurrentState objects
   * @return a new CurrentStates object containing all the given CurrentState objects
   */
  def createCurrentStates(states: java.util.List[CurrentState]): CurrentStates = CurrentStates(states.asScala)
}
