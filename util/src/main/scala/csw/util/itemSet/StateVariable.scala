package csw.util.itemSet

import csw.util.itemSet.ItemSets._

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
    val itemSetKey: ItemSetKey

    /**
      * an optional initial set of items (keys with values)
      */
    val items: ItemsData
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
    demand.prefix == current.prefix && demand.items == current.items

  /**
   * A state variable that indicates the ''demand'' or requested state.
   *
   * @param itemSetKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class DemandState(itemSetKey: ItemSetKey, items: ItemsData = Set.empty[Item[_]])
      extends ItemSet[DemandState] with ItemSetKeyData with StateVariable {

    override def create(data: ItemsData) = DemandState(itemSetKey, data)

    /**
     * This is here for Java to construct with String
     */
    def this(info: ItemSetInfo, itemSetKey: String) = this(ItemSetKey.stringToItemSetKey(itemSetKey))

    /**
     * Java API to create a DemandState from a SetupItemSet
     */
    def this(itemSet: Setup) = this(itemSet.prefix, itemSet.items)
  }

  object DemandState {
    /**
     * Converts a SetupItemSet to a DemandState
     */
    implicit def apply(itemSet: Setup): DemandState = DemandState(itemSet.prefix, itemSet.items)
  }

  /**
   * A state variable that indicates the ''current'' or actual state.
   *
   * @param itemSetKey identifies the target subsystem
   * @param items     an optional initial set of items (keys with values)
   */
  case class CurrentState(itemSetKey: ItemSetKey, items: ItemsData = Set.empty[Item[_]])
      extends ItemSet[CurrentState] with ItemSetKeyData with StateVariable {

    override def create(data: ItemsData) = CurrentState(itemSetKey, data)

    /**
     * This is here for Java to construct with String
     */
    def this(itemSetKey: String) = this(ItemSetKey.stringToItemSetKey(itemSetKey))

    /**
     * Java API to create a DemandState from a SetupItemSet
     */
    def this(itemSet: Setup) = this(itemSet.prefix, itemSet.items)

  }

  object CurrentState {
    /**
     * Converts a SetupItemSet to a CurrentState
     */
    implicit def apply(itemSet: Setup): CurrentState = CurrentState(itemSet.prefix, itemSet.items)

    /**
     * Java API to create a CurrentState from a SetupItemSet
     */
    def fromSetupItemSet(itemSet: Setup): CurrentState = CurrentState(itemSet.prefix, itemSet.items)
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
