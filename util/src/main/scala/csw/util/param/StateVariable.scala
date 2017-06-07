package csw.util.param

import csw.util.param.Parameters._

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.language.implicitConversions

object StateVariable {

  /**
   * Base trait for state variables
   */
  sealed trait StateVariable extends Serializable {
    /**
     * A name identifying the type of itemSet, such as "setup", "observe".
     * This is used in the JSON and toString output.
     */
    def typeName: String

    /**
     * identifies the target subsystem
     */
    val prefix: Prefix

    /**
     * an optional initial set of items (keys with values)
     */
    val paramSet: ParameterSet
  }

  /**
   * Type of a function that returns true if two state variables (demand and current)
   * match (or are close enough, which is implementation dependent)
   */
  type Matcher = (DemandState, CurrentState) => Boolean

  /**
   * The default matcher for state variables tests for an exact match
   *
   * @param demand  the demand state
   * @param current the current state
   * @return true if the demand and current states match (in this case, are equal)
   */
  def defaultMatcher(demand: DemandState, current: CurrentState): Boolean =
    demand.prefixStr == current.prefixStr && demand.paramSet == current.paramSet

  /**
   * A state variable that indicates the ''demand'' or requested state.
   *
   * @param prefix identifies the target subsystem
   * @param paramSet     an optional initial set of items (keys with values)
   */
  case class DemandState(prefix: Prefix, paramSet: ParameterSet = Set.empty[Parameter[_]])
      extends ParameterSetType[DemandState] with ParameterSetKeyData with StateVariable {

    override def create(data: ParameterSet) = DemandState(prefix, data)

    /**
     * This is here for Java to construct with String
     */
    def this(itemSetKey: String) = this(Prefix.stringToPrefix(itemSetKey))

    /**
     * Java API to create a DemandState from a SetupItemSet
     */
    def this(itemSet: Setup) = this(itemSet.prefixStr, itemSet.paramSet)
  }

  object DemandState {
    /**
     * Converts a SetupItemSet to a DemandState
     */
    implicit def apply(itemSet: Setup): DemandState = DemandState(itemSet.prefixStr, itemSet.paramSet)
  }

  /**
   * A state variable that indicates the ''current'' or actual state.
   *
   * @param prefix identifies the target subsystem
   * @param paramSet     an optional initial set of items (keys with values)
   */
  case class CurrentState(prefix: Prefix, paramSet: ParameterSet = Set.empty[Parameter[_]])
      extends ParameterSetType[CurrentState] with ParameterSetKeyData with StateVariable {

    override def create(data: ParameterSet) = CurrentState(prefix, data)

    /**
     * This is here for Java to construct with String
     */
    def this(itemSetKey: String) = this(Prefix.stringToPrefix(itemSetKey))

    /**
     * Java API to create a DemandState from a SetupItemSet
     */
    def this(itemSet: Setup) = this(itemSet.prefixStr, itemSet.paramSet)

  }

  object CurrentState {
    /**
     * Converts a SetupItemSet to a CurrentState
     */
    implicit def apply(itemSet: Setup): CurrentState = CurrentState(itemSet.prefixStr, itemSet.paramSet)

    /**
     * Java API to create a CurrentState from a SetupItemSet
     */
    def fromSetupItemSet(itemSet: Setup): CurrentState = CurrentState(itemSet.prefixStr, itemSet.paramSet)
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
