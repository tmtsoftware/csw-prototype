package csw.services.cs.core

import java.io.File
import java.util.Date

import akka.actor.ActorRefFactory
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A simple wrapper for ConfigManager that blocks while waiting for the return values to complete.
 */
case class BlockingConfigManager(manager: ConfigManager, timeout: Duration = 30.seconds)(implicit val context: ActorRefFactory) {

  import context.dispatcher

  /**
   * The name of this instance
   */
  val name: String = manager.name

  /**
   * Creates a file with the given path and data and optional comment.
   * An IOException is thrown if the file already exists.
   *
   * @param path the file path relative to the repository root
   * @param configData used to read the contents of the file
   * @param oversize true if the file is large and requires special handling (external storage)
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def create(path: File, configData: ConfigData, oversize: Boolean = false, comment: String = ""): ConfigId =
    Await.result(manager.create(path, configData, oversize, comment), timeout)

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the file path relative to the repository root
   * @param configData used to read the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def update(path: File, configData: ConfigData, comment: String = ""): ConfigId =
    Await.result(manager.update(path, configData, comment), timeout)

  /**
   * Creates the file with the given path, or updates it, if it already exists.
   *
   * @param path the file path relative to the repository root
   * @param configData used to read the contents of the file
   * @param oversize true if the file is large and requires special handling (external storage)
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def createOrUpdate(path: File, configData: ConfigData, oversize: Boolean = false, comment: String = ""): ConfigId =
    Await.result(manager.createOrUpdate(path, configData, oversize, comment), timeout)

  /**
   * Gets and returns the file stored under the given path.
   *
   * @param path the file path relative to the repository root
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return an object that can be used to access the file's data, if found
   */
  def get(path: File, id: Option[ConfigId] = None): Option[ConfigData] =
    Await.result(manager.get(path, id), timeout)

  /**
   * Gets the file as it existed on or before the given date
   * @param path the file path relative to the repository root
   * @param date the target date
   * @return an object that can be used to access the file's data, if found
   */
  def get(path: File, date: Date): Option[ConfigData] =
    Await.result(manager.get(path, date), timeout)

  /**
   * Returns true if the given path exists and is being managed
   *
   * @param path the file path relative to the repository root
   * @return true the file exists
   */
  def exists(path: File): Boolean = Await.result(manager.exists(path), timeout)

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the file path relative to the repository root
   */
  def delete(path: File, comment: String = "deleted"): Unit =
    Await.result(manager.delete(path, comment), timeout)

  /**
   * Returns a list containing all of the known config files
   *
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): List[ConfigFileInfo] = Await.result(manager.list(), timeout)

  /**
   * Returns a list of all known versions of a given path
   *
   * @param path the file path relative to the repository root
   * @param maxResults the maximum number of history results to return (default: unlimited)
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: File, maxResults: Int = Int.MaxValue): List[ConfigFileHistory] =
    Await.result(manager.history(path, maxResults), timeout)

  /**
   * Sets the "default version" of the file with the given path.
   * If this method is not called, the default version will always be the latest version.
   * After calling this method, the version with the given Id will be the default.
   *
   * @param path the file path relative to the repository root
   * @param id an optional id used to specify a specific version
   *           (by default the id of the latest version is used)
   */
  def setDefault(path: File, id: Option[ConfigId] = None): Unit =
    Await.result(manager.setDefault(path, id), timeout)

  /**
   * Resets the "default version" of the file with the given path to be always the latest version.
   *
   * @param path the file path relative to the repository root
   */
  def resetDefault(path: File): Unit =
    Await.result(manager.resetDefault(path), timeout)

  /**
   * Gets and returns the default version of the file stored under the given path.
   * If no default was set, this returns the latest version.
   *
   * @param path the file path relative to the repository root
   * @return an object that can be used to access the file's data, if found
   */
  def getDefault(path: File): Option[ConfigData] =
    Await.result(manager.getDefault(path), timeout)
}

