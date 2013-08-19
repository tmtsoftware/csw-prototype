package org.tmt.csw.cs.api

import java.util.Date
import java.io.File

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
  def create(path: File, configData: ConfigData, comment: String = "") : ConfigId

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def update(path: File, configData: ConfigData, comment: String = "") : ConfigId

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *            (by default the latest version is returned)
   * @return an object containing the configuration data, if found
   */
  def get(path: File, id: Option[ConfigId] = None) : Option[ConfigData]

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return true if the file exists
   */
  def exists(path: File) : Boolean

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   */
  def delete(path: File, comment: String = "deleted"): Unit

  /**
   * Returns a list containing all known configuration files
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): List[ConfigFileInfo]

  /**
   * Returns a list of all known versions of a given path
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: File) : List[ConfigFileHistory]
}

/**
 * Type of an id returned from ConfigManager create or update methods
 */
trait ConfigId

/**
 * Interface implemented by the configuration data objects being managed
 */
trait ConfigData {
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes : Array[Byte]
}

/**
 * Holds information about a specific version of a config file
 */
case class ConfigFileHistory(id: ConfigId, comment: String, time: Date)

/**
 * Contains information about a config file stored in the config service
 */
case class ConfigFileInfo(path: File, id: ConfigId, comment: String)

