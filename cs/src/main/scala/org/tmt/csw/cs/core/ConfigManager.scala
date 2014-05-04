package org.tmt.csw.cs.core

import java.util.Date
import java.io.File
import java.nio.file.{Paths, Files}

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
   * @return true the file exists
   */
  def exists(path: File) : Boolean

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   */
  def delete(path: File, comment: String = "deleted"): Unit

  /**
   * Returns a list containing all of the known config files
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
trait ConfigId {
  val id: String
}

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

/**
 * Type of an id returned from ConfigManager create or update methods.
 * Holds the Git id for the file.
 */
case class GitConfigId(id: String) extends ConfigId

/**
 * Represents the contents of a config file
 */
case class ConfigString(str: String) extends ConfigData {
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = str.getBytes
  // TODO: Note: maybe serializing the string would be safer here? (no charset handling)

  override def toString: String = str
}

/**
 * Represents a configuration file
 */
case class ConfigBytes(bytes: Array[Byte]) extends ConfigData {

  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = bytes


  /**
   * Should only be used for debugging info (no charset handling)
   * @return contents as string
   */
  override def toString: String = {
    new String(bytes)
  }
}

/**
 * Represents a configuration file
 */
case class ConfigFile(file: File) extends ConfigData {
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = {
    Files.readAllBytes(Paths.get(file.getPath))
  }

  override def toString: String = {
    new String(Files.readAllBytes(Paths.get(file.getPath)))
  }
}
