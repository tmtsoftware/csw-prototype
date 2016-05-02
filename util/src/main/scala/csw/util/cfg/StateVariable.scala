package csw.util.cfg

import csw.util.cfg.Configurations.{ConfigKey, ConfigType, SetupConfig}
import scala.language.implicitConversions

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
    demand.prefix == current.prefix && demand.data == current.data

  /**
   * A state variable that indicates the ''demand'' or requested state.
   *
   * @param configKey ket for the data
   * @param data      the data
   */
  case class DemandState(configKey: ConfigKey, data: ConfigData = ConfigData())
      extends ConfigType[DemandState] with StateVariable {

    override protected def create(data: ConfigData) = DemandState(configKey, data)

    override def toString = doToString("demand")
  }

  object DemandState {
    /**
     * Converts a SetupConfig to a DemandState
     */
    implicit def apply(config: SetupConfig): DemandState = DemandState(config.prefix, config.data)
  }

  /**
   * A state variable that indicates the ''current'' or actual state.
   *
   * @param configKey ket for the data
   * @param data      the data
   */
  case class CurrentState(configKey: ConfigKey, data: ConfigData = ConfigData())
      extends ConfigType[CurrentState] with StateVariable {

    override protected def create(data: ConfigData) = CurrentState(configKey, data)

    override def toString = doToString("current")
  }

  object CurrentState {
    /**
     * Converts a SetupConfig to a CurrentState
     */
    implicit def apply(config: SetupConfig): CurrentState = CurrentState(config.prefix, config.data)
  }

}
