package csw.util.config

/**
 * Base trait for state variables
 */
sealed trait StateVariable {

  import StateVariable._

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

  /**
   * An implementation supplied function that returns true if two state variables match (or are close enough)
   */
  def matcher: Matcher

  override def toString = s"$extKey -> $data"

}

object StateVariable {

  /**
   * Type of a function that returns true if two state variables (demand and current)
   * match (or are close enough, which is implementation dependent)
   */
  type Matcher = (StateVariable, StateVariable) ⇒ Boolean

  /**
   * The default matcher for state variables tests for an exact match
   * @param demand the demand state
   * @param current the current state
   * @return true if the demand and current states match (in this case, are equal)
   */
  def defaultMatcher(demand: StateVariable, current: StateVariable) =
    demand.prefix == current.prefix && demand.data == current.data

  /**
   * The demand (requested) state for the given prefix.
   */
  case class DemandState(prefix: String, data: ConfigData = ConfigData(), matcher: Matcher = defaultMatcher)
      extends StateVariable {

    override val extKey = s"demand:$prefix"

    /**
     * Adds a key/value pair to the data map and returns the new state
     */
    def set(key: Key)(value: key.Value): DemandState = DemandState(prefix, data.set(key)(value))

    /**
     * Removes a key/value pair from the data map and returns the new state
     */
    def remove(key: Key): DemandState = DemandState(prefix, data.remove(key))

    /**
     * Returns true if the given current state matches this demand state
     */
    def matches(current: CurrentState): Boolean = matcher(this, current)
  }

  //  object DemandState {
  //    import scala.pickling.Defaults._
  //    import scala.pickling.binary._
  //    /**
  //     * Defines the automatic conversion to a ByteString and back again.
  //     */
  //    implicit val byteStringFormatter = new ByteStringFormatter[DemandState] {
  //      def serialize(t: DemandState): ByteString = {
  //        ByteString(t.pickle.value)
  //      }
  //
  //      def deserialize(bs: ByteString): DemandState = {
  //        val ar = Array.ofDim[Byte](bs.length)
  //        bs.asByteBuffer.get(ar)
  //        ar.unpickle[DemandState]
  //      }
  //    }
  //  }

  /**
   * The current (actual) state for the given prefix.
   */
  case class CurrentState(prefix: String, data: ConfigData = ConfigData(), matcher: Matcher = defaultMatcher)
      extends StateVariable {

    override val extKey = s"current:$prefix"

    /**
     * Adds a key/value pair to the data map and returns the new state
     */
    def set(key: Key)(value: key.Value): CurrentState = CurrentState(prefix, data.set(key)(value))

    /**
     * Removes a key/value pair from the data map and returns the new state
     */
    def remove(key: Key): CurrentState = CurrentState(prefix, data.remove(key))

    /**
     * Returns true if the given demand state matches this current state
     */
    def matches(demand: DemandState): Boolean = matcher(demand, this)
  }

  //  object CurrentState {
  //    import scala.pickling.Defaults._
  //    import scala.pickling.binary._
  //    /**
  //     * Defines the automatic conversion to a ByteString and back again.
  //     */
  //    implicit val byteStringFormatter = new ByteStringFormatter[CurrentState] {
  //      def serialize(t: CurrentState): ByteString = {
  //        ByteString(t.pickle.value)
  //      }
  //
  //      def deserialize(bs: ByteString): CurrentState = {
  //        val ar = Array.ofDim[Byte](bs.length)
  //        bs.asByteBuffer.get(ar)
  //        ar.unpickle[CurrentState]
  //      }
  //    }
  //  }
}

