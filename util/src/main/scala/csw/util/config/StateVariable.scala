package csw.util.config

/**
 * Base trait for state variables
 */
sealed trait StateVariable {

  /**
   * The prefix for component
   */
  def prefix: String

  /**
   * Holds the key/value pairs
   */
  def data: ConfigData

  /**
   * The key used to store the data externally
   */
  def extKey: String

  /**
   * The number of key/value pairs
   */
  def size = data.size

  /**
   * Returns the value for the key, if found
   */
  def get(key: Key): Option[key.Value] = data.get(key)

  override def toString = s"$extKey -> $data"

}

object StateVariable {

  /**
   * Type of a function that returns true if two state variables (demand and current)
   * match (or are close enough, which is implementation dependent)
   */
  type Matcher = (DemandState, CurrentState) ⇒ Boolean

  /**
   * The default matcher for state variables tests for an exact match
   * @param demand the demand state
   * @param current the current state
   * @return true if the demand and current states match (in this case, are equal)
   */
  def defaultMatcher(demand: DemandState, current: CurrentState): Boolean =
    demand.prefix == current.prefix && demand.data == current.data

  /**
   * The demand (requested) state for the given prefix.
   */
  case class DemandState(prefix: String, data: ConfigData = ConfigData())
      extends StateVariable {

    override def extKey = DemandState.makeExtKey(prefix)

    /**
     * Adds a key/value pair to the data map and returns the new state
     */
    def set(key: Key)(value: key.Value): DemandState = DemandState(prefix, data.set(key)(value))

    /**
     * Removes a key/value pair from the data map and returns the new state
     */
    def remove(key: Key): DemandState = DemandState(prefix, data.remove(key))
  }

  object DemandState {
    /**
     * Returns the key used to store the demand state for the given prefix
     */
    def makeExtKey(prefix: String): String = s"demand:$prefix"
  }

  /**
   * The current (actual) state for the given prefix.
   */
  case class CurrentState(prefix: String, data: ConfigData = ConfigData(), matcher: Matcher = defaultMatcher)
      extends StateVariable {

    override def extKey = CurrentState.makeExtKey(prefix)

    /**
     * Adds a key/value pair to the data map and returns the new state
     */
    def set(key: Key)(value: key.Value): CurrentState = CurrentState(prefix, data.set(key)(value))

    /**
     * Removes a key/value pair from the data map and returns the new state
     */
    def remove(key: Key): CurrentState = CurrentState(prefix, data.remove(key))
  }

  object CurrentState {
    /**
     * Returns the key used to store the current state for the given prefix
     */
    def makeExtKey(prefix: String): String = s"current:$prefix"

    /**
     * Returns the key used to store the current state for the given demand state
     */
    def makeExtKey(demand: DemandState): String = s"current:${demand.prefix}"
  }

}

