package org.tmt.csw.cs.akka

import java.io.File
import scala.concurrent.{ExecutionContextExecutor, Future}
import org.tmt.csw.cs.core._
import org.tmt.csw.cs.core.ConfigFileInfo
import java.net.URI
import org.tmt.csw.cs.core.git.GitConfigManager

/**
 * Wraps access to a ConfigManager instance in Futures to avoid blocking.
 */
case class NonBlockingConfigManager(manager: ConfigManager)(implicit dispatcher: ExecutionContextExecutor) {

  /**
   * Creates a config file with the given path and data and optional comment.
   * An IOException is generated if the file already exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def create(path: File, configData: ConfigData, comment: String = "") : Future[ConfigId] =
    Future(manager.create(path, configData, comment))

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is generated if the file does not exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def update(path: File, configData: ConfigData, comment: String = "") : Future[ConfigId] =
    Future(manager.update(path, configData, comment))

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *            (by default the latest version is returned)
   * @return an object containing the configuration data, if found
   */
  def get(path: File, id: Option[ConfigId] = None) : Future[Option[ConfigData]] =
    Future(manager.get(path, id))

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return true the file exists
   */
  def exists(path: File) : Future[Boolean] =
    Future(manager.exists(path))

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   */
  def delete(path: File, comment: String = "deleted"): Future[Unit] =
    Future(manager.delete(path, comment))

  /**
   * Returns a list containing all of the known config files
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): Future[List[ConfigFileInfo]] =
    Future(manager.list())

  /**
   * Returns a list of all known versions of a given path
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: File) : Future[List[ConfigFileHistory]] =
    Future(manager.history(path))
}

/**
 * Wraps a GitConfigManager constructor in a Future to avoid blocking.
 */
object NonBlockingGitConfigManager {
  /**
   * Creates and returns a wrapped GitConfigManager instance using the given directory as the
   * local Git repository root (directory containing .git dir) and the given
   * URI as the remote, central Git repository.
   * If the local repository already exists, it is opened, otherwise it is created.
   * An exception is generated if the remote repository does not exist.
   *
   * @param gitWorkDir top level directory to use for storing configuration files and the local git repository (under .git)
   * @param remoteRepo the URI of the remote, main repository
   *
   * @return a wrapped GitConfigManager configured to use the given local and remote repositories
   */
  def apply(gitWorkDir: File, remoteRepo: URI)(implicit dispatcher: ExecutionContextExecutor): Future[NonBlockingConfigManager] =
    Future(NonBlockingConfigManager(GitConfigManager(gitWorkDir, remoteRepo)))
}
