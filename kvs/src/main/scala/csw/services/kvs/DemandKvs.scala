package csw.services.kvs

import akka.actor.ActorRefFactory
import csw.util.cfg.Configurations.SetupConfig
import Implicits.setupConfigKvsFormatter

import scala.concurrent.{ ExecutionContext, Future }

object DemandKvs {
  /**
   * Returns a new DemandKvs
   */
  def apply(settings: KvsSettings)(implicit _system: ActorRefFactory): DemandKvs = {
    import _system.dispatcher
    DemandKvs(KeyValueStore[SetupConfig](settings))
  }

  private def demandPrefix(prefix: String): String = s"demand:$prefix"
  private def demandPrefix(config: SetupConfig): String = demandPrefix(config.prefix)
}

/**
 * A class for setting and getting demand state variables.
 * A demand state variable is a SetupConfig stored under the key: s"demand:$prefix",
 * while a current state variable is just a SetupConfig stored under it's own prefix.
 */
case class DemandKvs(kvs: KeyValueStore[SetupConfig])(implicit ec: ExecutionContext) {
  import DemandKvs._

  /**
   * Sets the demand value for the config (key is based on the config's prefix)
   *
   * @param config the value to store
   * @param history optional number of previous values to store
   * @return a future indicating if/when the operation has completed
   */
  def setDemand(config: SetupConfig, history: Int = 0): Future[Unit] =
    kvs.set(demandPrefix(config), config, history)

  /**
   * Gets the demand value for the given config prefix
   *
   * @param prefix the prefix (key) for the config tohet
   * @return the setup config, if (and when) found
   */
  def getDemand(prefix: String): Future[Option[SetupConfig]] =
    kvs.get(demandPrefix(prefix))

  /**
   * Gets a list of the n most recent demand values for the given prefix
   * @param prefix the config's prefix
   * @param n the max number of values to get
   * @return future sequence of demand values, ordered by most recent
   */
  def getDemandHistory(prefix: String, n: Int): Future[Seq[SetupConfig]] =
    kvs.getHistory(demandPrefix(prefix), n)

  /**
   * Deletes the given demand value from the store
   * @return a future indicating if/when the operation has completed
   */
  def deleteDemand(prefix: String): Future[Unit] = kvs.delete(demandPrefix(prefix)).map(_ ⇒ ())
}
