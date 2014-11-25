package csw.services.cs.core

import java.io.File
import java.nio.file.{ Files, Paths }
import java.util.Date

import scala.concurrent.Future

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
   * @param oversize true if the file is large and requires special handling (external storage)
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def create(path: File, configData: ConfigData, oversize: Boolean = false, comment: String = ""): Future[ConfigId]

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def update(path: File, configData: ConfigData, comment: String = ""): Future[ConfigId]

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the config file path
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return a future object containing the configuration data, if found
   */
  def get(path: File, id: Option[ConfigId] = None): Future[Option[ConfigData]]

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return true the file exists
   */
  def exists(path: File): Future[Boolean]

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   */
  def delete(path: File, comment: String = "deleted"): Future[Unit]

  /**
   * Returns a list containing all of the known config files
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): Future[List[ConfigFileInfo]]

  /**
   * Returns a list of all known versions of a given path
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: File): Future[List[ConfigFileHistory]]
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
  //  /**
  //   * @return a stream of XXX TODO...
  //   */
  //  def getSource: Source[ByteString]

  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte]
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
 * Represents the contents of a config file as a String
 */
case class ConfigString(str: String) extends ConfigData {
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = str.getBytes

  // TODO: charset handling?

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
  override def toString: String = new String(bytes)
}

/**
 * Represents a configuration file
 */
case class ConfigFile(file: File) extends ConfigData {
  // XXX Note: If we delay reading in the file, the contents could change before we read it!
  // XXX TODO FIXME: Don't read into memory (but watch out for modification before reading!)
  val bytes = Files.readAllBytes(Paths.get(file.getPath))
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = bytes

  override def toString: String = new String(bytes)
}
