package org.tmt.csw.cs.api

/**
 * Defines an interface for storing and retrieving configuration information
 */
trait ConfigManager {

  /**
   * Creates a config file with the given path and data and optional comment.
   * An IOException is thrown if the file already exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def create(path: String, configData: ConfigData, comment: String = "") : String

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def update(path: String, configData: ConfigData, comment: String = "") : String

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *            (by default the latest version is returned)
   * @return an object containing the configuration data, if found
   */
  def get(path: String, id: Option[String] = None) : Option[ConfigData]

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return true if the file exists
   */
  def exists(path: String) : Boolean

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   */
  def delete(path: String, comment: String = "deleted")

  /**
   * Returns a list containing all known configuration files
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): List[ConfigFileInfo]

  /**
   * Returns a list of all known versions of a given path
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: String) : List[ConfigFileHistory]

}
