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

//  /**
//   * Updates the given config file
//   *
//   * @param path the config file path
//   * @param configData the file contents
//   * @param comment an optional comment to associate with this version
//   * @return an id that can be used to fetch this version of the config file at a later time
//   */
//  def update(path: String, configData: ConfigData, comment: String = "") : String

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   * @param comment an optional comment
   */
  def delete(path: String, configData: ConfigData, comment: String = "")

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
   * Returns a list containing all known configuration files
   */
  def list() : List[String] // TODO: return list of tuple (name, id, description)?

  /**
   * Returns a list of tuples (id, comment) containing all known version ids and the associated comments
   * for the given configuration path
   *
   * @param path the configuration path
   * @return a list containing one tuple (id, comment) for each version of the given configuration path
   */
  def versions(path: String) : List[(String, String)]

}
