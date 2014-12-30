package csw.services.cs.core

import java.io.{ ByteArrayOutputStream, File, FileOutputStream, OutputStream }
import java.nio.file.Files
import java.util.Date

import akka.actor.ActorRefFactory
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ ForeachSink, Source }
import akka.util.ByteString
import csw.services.apps.configServiceAnnex.FileUtils

import scala.concurrent.Future
import scala.util.Try

/**
 * Defines an interface for storing and retrieving configuration information
 */
trait ConfigManager {

  /**
   * The name of this instance
   */
  val name: String

  /**
   * Creates a config file with the given path and data and optional comment.
   * An IOException is thrown if the file already exists.
   *
   * @param path the file path relative to the repository root
   * @param configData used to read the contents of the file
   * @param oversize true if the file is large and requires special handling (external storage)
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def create(path: File, configData: ConfigData, oversize: Boolean = false, comment: String = ""): Future[ConfigId]

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the file path relative to the repository root
   * @param configData used to read the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def update(path: File, configData: ConfigData, comment: String = ""): Future[ConfigId]

  /**
   * Gets and returns the file stored under the given path.
   *
   * @param path the file path relative to the repository root
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return a future object that can be used to access the file's data, if found
   */
  def get(path: File, id: Option[ConfigId] = None): Future[Option[ConfigData]]

  /**
   * Returns true if the given path exists and is being managed
   * @param path the file path relative to the repository root
   * @return true the file exists
   */
  def exists(path: File): Future[Boolean]

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the file path relative to the repository root
   */
  def delete(path: File, comment: String = "deleted"): Future[Unit]

  /**
   * Returns a list containing all of the known config files
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): Future[List[ConfigFileInfo]]

  /**
   * Returns a list of all known versions of a given path
   * @param path the file path relative to the repository root
   * @param maxResults the maximum number of history results to return (default: unlimited)
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: File, maxResults: Int = Int.MaxValue): Future[List[ConfigFileHistory]]

  /**
   * Sets the "default version" of the file with the given path.
   * If this method is not called, the default version will always be the latest version.
   * After calling this method, the version with the given Id will be the default.
   *
   * @param path the file path relative to the repository root
   * @param id an optional id used to specify a specific version
   *           (by default the id of the latest version is used)
   * @return a future result
   */
  def setDefault(path: File, id: Option[ConfigId] = None): Future[Unit]

  /**
   * Resets the "default version" of the file with the given path to be always the latest version.
   *
   * @param path the file path relative to the repository root
   * @return a future result
   */
  def resetDefault(path: File): Future[Unit]

  /**
   * Gets and returns the default version of the file stored under the given path.
   * If no default was set, this returns the latest version.
   *
   * @param path the file path relative to the repository root
   * @return a future object that can be used to access the file's data, if found
   */
  def getDefault(path: File): Future[Option[ConfigData]]
}

/**
 * Type of an id returned from ConfigManager create or update methods
 */
trait ConfigId {
  val id: String
}

object ConfigId {
  def apply(id: String): ConfigId = GitConfigId(id)
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
 * This trait represents the contents of the files being managed.
 */
trait ConfigData {
  /**
   * Returns a stream which can be used to read the data
   */
  def source: Source[ByteString]

  /**
   * Writes the contents of the source to the given output stream.
   */
  def writeToOutputStream(out: OutputStream)(implicit context: ActorRefFactory): Future[Unit] = {
    import context.dispatcher
    implicit val materializer = FlowMaterializer()
    val sink = ForeachSink[ByteString] { bytes ⇒
      out.write(bytes.toArray)
    }
    val materialized = source.to(sink).run()
    // ensure the output file is closed when done
    val result = materialized.get(sink)
    result.andThen { case _ ⇒ Try(out.close()) }
    result
  }

  /**
   * Writes the contents of the source to the given file.
   */
  def writeToFile(file: File)(implicit context: ActorRefFactory): Future[Unit] = {
    val path = file.toPath
    if (!Files.isDirectory(path.getParent))
      Files.createDirectories(path.getParent)
    val out = new FileOutputStream(file)
    writeToOutputStream(out)
  }

  /**
   * Returns a future string by reading the source.
   */
  def toFutureString(implicit context: ActorRefFactory): Future[String] = {
    implicit val materializer = FlowMaterializer()
    import context.dispatcher
    val out = new ByteArrayOutputStream
    val sink = ForeachSink[ByteString] { bytes ⇒
      out.write(bytes.toArray)
    }
    val materialized = source.to(sink).run()
    for { _ ← materialized.get(sink) } yield out.toString
  }
}

object ConfigData {
  def apply(str: String): ConfigData = ConfigString(str)

  def apply(bytes: Array[Byte]): ConfigData = ConfigBytes(bytes)

  def apply(file: File, chunkSize: Int = 4096): ConfigData = ConfigFile(file, chunkSize)

  def apply(source: Source[ByteString]): ConfigData = ConfigSource(source)
}

case class ConfigString(str: String) extends ConfigData {
  override def source: Source[ByteString] = Source(List(ByteString(str.getBytes)))

  override def toString: String = str
}

case class ConfigBytes(bytes: Array[Byte]) extends ConfigData {
  override def source: Source[ByteString] = Source(List(ByteString(bytes)))

  override def toString: String = new String(bytes)
}

case class ConfigFile(file: File, chunkSize: Int = 4096) extends ConfigData {
  override def source: Source[ByteString] = {
    val mappedByteBuffer = FileUtils.mmap(file.toPath)
    val iterator = new FileUtils.ByteBufferIterator(mappedByteBuffer, chunkSize)
    Source(() ⇒ iterator)
  }
}

case class ConfigSource(override val source: Source[ByteString]) extends ConfigData