package org.tmt.csw.cs.akka

import org.tmt.csw.cs.api._
import scala.concurrent.Future
import akka.pattern.ask
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.duration._
import ConfigServiceActor._

/**
 * Adds a convenience layer over the Akka actor interface to the configuration service.
 * This is like the ConfigService API, but returns Akka Futures with the correct return types instead of objects.
 */
class ConfigServiceClient(configServiceActor : ActorRef)  {

  val duration = 5.seconds
  implicit val timeout = Timeout(5.seconds)

  /**
   * Creates a config file with the given path and data and optional comment.
   * An IOException is thrown if the file already exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a Future wrapping a unique id that can be used to refer to the file
   */
  def create(path: String, configData: ConfigData, comment: String): Future[ConfigId] = {
    (configServiceActor ? CreateRequest(path, configData, comment)).mapTo[ConfigId]
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
  def update(path: String, configData: ConfigData, comment: String): Future[ConfigId] = {
    (configServiceActor ? UpdateRequest(path, configData, comment)).mapTo[ConfigId]
  }

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return a Future wrapping an object containing the configuration data, if found
   */
  def get(path: String, id: Option[ConfigId] = None): Future[Option[ConfigData]] = {
    (configServiceActor ? GetRequest(path, id)).mapTo[Option[ConfigData]]
  }

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return a Future wrapping true if the file exists
   */
  def exists(path: String): Future[Boolean] = {
    (configServiceActor ? ExistsRequest(path)).mapTo[Boolean]
  }

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   * @return a Future wrapping Unit (so it is possible to tell when the operation is done or failed)
   */
  def delete(path: String, comment: String) : Future[Unit] = {
    (configServiceActor ? DeleteRequest(path)).mapTo[Unit]
  }

  /**
   * Returns a list containing all known configuration files
   * @return a Future wrapping a list containing one ConfigFileInfo object for each known config file
   */
  def list(): Future[List[ConfigFileInfo]] = {
    (configServiceActor ? ListRequest).mapTo[List[ConfigFileInfo]]
  }

  /**
   * Returns a list of all known versions of a given path
   * @return a Future wrapping a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: String): Future[List[ConfigFileHistory]] = {
    (configServiceActor ? HistoryRequest(path)).mapTo[List[ConfigFileHistory]]
  }
}

