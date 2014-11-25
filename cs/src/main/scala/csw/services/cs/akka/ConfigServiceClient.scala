package csw.services.cs.akka

import scala.concurrent.Future
import akka.pattern.ask
import akka.actor.{ ActorSystem, ActorRef }
import akka.util.Timeout
import ConfigServiceActor._
import java.io.File
import csw.services.cs.core._

/**
 * Adds a convenience layer over the Akka actor interface to the configuration service.
 * This is like the ConfigService API, but returns Akka Futures with the correct return types instead of objects.
 *
 * @param system the caller's actor system
 * @param configServiceActor the config service actor reference to use
 * @param timeout amount of time to wait for config service operations to complete
 */
case class ConfigServiceClient(system: ActorSystem, configServiceActor: ActorRef)(implicit timeout: Timeout) {

  import system.dispatcher

  /**
   * Creates a config file with the given path and data and optional comment.
   * An IOException is thrown if the file already exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param oversize true if the file is large and requires special handling (external storage)
   * @param comment an optional comment to associate with this file
   * @return a Future wrapping a unique id that can be used to refer to the file
   */
  def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] = {
    (configServiceActor ? CreateRequest(path, configData, oversize, comment))
      .mapTo[CreateResult].map(_.configId.get)
  }

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a Future wrapping a unique id that can be used to refer to the file
   */
  def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {
    (configServiceActor ? UpdateRequest(path, configData, comment)).
      mapTo[UpdateResult].map(_.configId.get)
  }

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return a Future wrapping an object containing the configuration data, if found
   */
  def get(path: File, id: Option[ConfigId] = None): Future[Option[ConfigData]] = {
    (configServiceActor ? GetRequest(path, id)).mapTo[GetResult].map(_.configData.get)
  }

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return a Future wrapping true if the file exists
   */
  def exists(path: File): Future[Boolean] = {
    (configServiceActor ? ExistsRequest(path)).mapTo[ExistsResult].map(_.exists.get)
  }

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   * @return a Future wrapping Unit (so it is possible to tell when the operation is done or failed)
   */
  def delete(path: File, comment: String): Future[Unit] = {
    (configServiceActor ? DeleteRequest(path)).mapTo[DeleteResult].map(_.status.get)
  }

  /**
   * Returns a list containing all known configuration files
   * @return a Future wrapping a list containing one ConfigFileInfo object for each known config file
   */
  def list(): Future[List[ConfigFileInfo]] = {
    (configServiceActor ? ListRequest).mapTo[ListResult].map(_.list.get)
  }

  /**
   * Returns a list of all known versions of a given path
   * @return a Future wrapping a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: File): Future[List[ConfigFileHistory]] = {
    (configServiceActor ? HistoryRequest(path)).mapTo[HistoryResult].map(_.history.get)
  }
}

