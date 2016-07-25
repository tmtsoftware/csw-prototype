package csw.services.cs.core

import java.io.{ByteArrayOutputStream, File, FileOutputStream, OutputStream}
import java.nio.file.{Files, StandardCopyOption}
import java.util.Date

import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import csw.services.apps.configServiceAnnex.FileUtils

import scala.concurrent.{ExecutionContext, Future}
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
   * Creates a file with the given path and data and optional comment.
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
   * Creates the file with the given path, or updates it, if it already exists.
   *
   * @param path the file path relative to the repository root
   * @param configData used to read the contents of the file
   * @param oversize true if the file is large and requires special handling (external storage)
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def createOrUpdate(path: File, configData: ConfigData, oversize: Boolean = false, comment: String = ""): Future[ConfigId]

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
   * Gets the file as it existed on or before the given date
   * @param path the file path relative to the repository root
   * @param date the target date
   * @return a future object that can be used to access the file's data, if found
   */
  def get(path: File, date: Date)(implicit ec: ExecutionContext): Future[Option[ConfigData]] = {
    for {
      hist <- history(path).map(_.find(_.time.getTime < date.getTime))
      if hist.nonEmpty
      result <- get(path, hist.map(_.id))
    } yield result
  }

  /**
   * Returns true if the given path exists and is being managed
   *
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
   *
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): Future[List[ConfigFileInfo]]

  /**
   * Returns a list of all known versions of a given path
   *
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
  def apply(id: String): ConfigId = ConfigIdImpl(id)
  def apply(id: Long): ConfigId = ConfigIdImpl(id.toString)
}

/**
 * Type of an id returned from ConfigManager create or update methods.
 */
case class ConfigIdImpl(id: String) extends ConfigId

/**
 * Holds information about a specific version of a config file
 */
case class ConfigFileHistory(id: ConfigId, comment: String, time: Date)

/**
 * Contains information about a config file stored in the config service
 */
case class ConfigFileInfo(path: File, id: ConfigId, comment: String)

/**
 * This trait represents the contents of the files being managed.
 * It is based on Akka streams.
 */
trait ConfigData {
  /**
   * Returns a stream which can be used to read the data
   */
  def source: Source[ByteString, Any]

  /**
   * Writes the contents of the source to the given output stream.
   */
  def writeToOutputStream(out: OutputStream)(implicit context: ActorRefFactory): Future[Unit] = {
    import context.dispatcher
    implicit val materializer = ActorMaterializer()
    val sink = Sink.foreach[ByteString] { bytes =>
      out.write(bytes.toArray)
    }
    val materialized = source.runWith(sink)
    // ensure the output file is closed when done
    for {
      _ <- materialized
    } yield {
      Try(out.close())
    }
  }

  /**
   * Writes the contents of the source to the given file.
   */
  def writeToFile(file: File)(implicit context: ActorRefFactory): Future[Unit] = {
    import context.dispatcher
    val path = file.toPath
    val dir = path.getParent
    if (!Files.isDirectory(dir))
      Files.createDirectories(dir)

    // Write to a tmp file and then rename
    val tmpFile = File.createTempFile(file.getName, null, dir.toFile)
    val out = new FileOutputStream(tmpFile)
    for {
      _ <- writeToOutputStream(out)
    } yield {
      Files.move(tmpFile.toPath, path, StandardCopyOption.ATOMIC_MOVE)
    }
  }

  /**
   * Returns a future string by reading the source.
   */
  def toFutureString(implicit context: ActorRefFactory): Future[String] = {
    implicit val materializer = ActorMaterializer()
    import context.dispatcher
    val out = new ByteArrayOutputStream
    val sink = Sink.foreach[ByteString] { bytes =>
      out.write(bytes.toArray)
    }
    val materialized = source.runWith(sink)
    for (_ <- materialized) yield out.toString
  }
}

/**
 * Provides various alternatives for constructing the data to be stored in the config service.
 */
object ConfigData {
  /**
   * The data is contained in the string
   */
  def apply(str: String): ConfigData = ConfigString(str)

  /**
   * Takes the data from the byte array
   */
  def apply(bytes: Array[Byte]): ConfigData = ConfigBytes(bytes)

  /**
   * Initialize with the contents of the given file.
   *
   * @param file the data source
   * @param chunkSize the block or chunk size to use when streaming the data
   */
  def apply(file: File, chunkSize: Int = 4096): ConfigData = ConfigFile(file, chunkSize)

  /**
   * The data source can be any byte string
   */
  def apply(source: Source[ByteString, Any]): ConfigData = ConfigSource(source)
}

case class ConfigString(str: String) extends ConfigData {
  override def source: Source[ByteString, NotUsed] = Source(List(ByteString(str.getBytes)))

  override def toString: String = str
}

case class ConfigBytes(bytes: Array[Byte]) extends ConfigData {
  override def source: Source[ByteString, NotUsed] = Source(List(ByteString(bytes)))

  override def toString: String = new String(bytes)
}

case class ConfigFile(file: File, chunkSize: Int = 4096) extends ConfigData {
  override def source: Source[ByteString, NotUsed] = {
    val mappedByteBuffer = FileUtils.mmap(file.toPath)
    val iterator = new FileUtils.ByteBufferIterator(mappedByteBuffer, chunkSize)
    Source.fromIterator(() => iterator)
  }
}

case class ConfigSource(override val source: Source[ByteString, Any]) extends ConfigData