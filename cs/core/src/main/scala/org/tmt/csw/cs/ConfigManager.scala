package org.tmt.csw.cs

/**
 * Defines an interface for storing and retrieving configuration information
 */
trait ConfigManager {

  /**
   * Creates or updates a config file with the given path and data and optional comment.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def put(path: String, configData: ConfigData, comment: String = "") : String

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
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   * @param comment an optional comment
   */
  def delete(path: String, configData: ConfigData, comment: String = "")

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
