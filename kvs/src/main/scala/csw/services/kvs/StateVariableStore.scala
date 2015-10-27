package csw.services.kvs

import akka.actor.ActorRefFactory
import csw.util.cfg.Configurations.StateVariable
import csw.util.cfg.Configurations.StateVariable.{ CurrentState, DemandState }

import scala.concurrent.Future

object StateVariableStore {
  private def demandPrefix(prefix: String): String = s"demand:$prefix"
  private def demandPrefix(config: DemandState): String = demandPrefix(config.prefix)
}

/**
 * A class for working with state variables.
 * There are two types of state variables: ''demand'' and ''current''.
 * The demand value is the requested value, while the current value indicates the actual value.
 *
 * A demand state variable is stored under the key: `demand`:''prefix'',
 * while a current state variable is stored under it's own prefix.
 *
 * @param settings settings from reference.conf or application.conf with the Redis server host/port information
 * @param _system Akka environment needed by the implementation
 */
case class StateVariableStore(settings: KvsSettings)(implicit _system: ActorRefFactory) {
  import _system.dispatcher
  import StateVariableStore._
  import Implicits._

  private val kvs = KeyValueStore[StateVariable](settings)

  // --- Set/Get Demand Values ---

  /**
   * Sets the demand value for the config (key is based on the config's prefix)
   *
   * @param config the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  def setDemand(config: DemandState, history: Int = 0): Future[Unit] =
    kvs.set(demandPrefix(config), config, history)

  /**
   * Gets the demand value for the given config prefix
   *
   * @param prefix the prefix (key) for the config to get
   * @return the demand config, if (and when) found
   */
  def getDemand(prefix: String): Future[Option[DemandState]] =
    kvs.get(demandPrefix(prefix)).mapTo[Option[DemandState]]

  /**
   * Gets a list of the n most recent demand values for the given prefix
   * @param prefix the config's prefix
   * @param n the max number of values to get
   * @return future sequence of demand values, ordered by most recent
   */
  def getDemandHistory(prefix: String, n: Int): Future[Seq[DemandState]] =
    kvs.getHistory(demandPrefix(prefix), n).mapTo[Seq[DemandState]]

  /**
   * Deletes the given demand value from the store
   * @return a future indicating if/when the operation has completed
   */
  def deleteDemand(prefix: String): Future[Unit] = kvs.delete(demandPrefix(prefix)).map(_ ⇒ ())

  // --- Set/Get Current Values ---

  /**
   * Sets the current value for the config (key is based on the config's prefix)
   *
   * @param config the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  def set(config: CurrentState, history: Int = 0): Future[Unit] =
    kvs.set(config.prefix, config, history)

  /**
   * Gets the current value for the given config prefix
   *
   * @param prefix the prefix (key) for the config to get
   * @return the current config, if (and when) found
   */
  def get(prefix: String): Future[Option[CurrentState]] =
    kvs.get(prefix).mapTo[Option[CurrentState]]

  /**
   * Gets a list of the n most recent current values for the given prefix
   * @param prefix the config's prefix
   * @param n the max number of values to get
   * @return future sequence of current values, ordered by most recent
   */
  def getHistory(prefix: String, n: Int): Future[Seq[CurrentState]] =
    kvs.getHistory(prefix, n).mapTo[Seq[CurrentState]]

  /**
   * Deletes the given current value from the store
   * @return a future indicating if/when the operation has completed
   */
  def delete(prefix: String): Future[Unit] = kvs.delete(prefix).map(_ ⇒ ())

}
