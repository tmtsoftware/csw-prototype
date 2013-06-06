package org.tmt.csw.cmd.core

import com.typesafe.config._
import java.io.{Reader, FileReader, File, StringReader}
import scala.collection.JavaConverters._

object Configuration {
  val toStringOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
  val formatOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)

  def waitConfig(forResume: Boolean, configId: Int, obsId: String) : Configuration = {
    Configuration(Map("wait" -> Map("forResume" -> forResume, "configId" -> configId, "obsId" -> obsId)))
  }

  /**
   * Initialize with an existing typesafe Config object
   */
  def apply(config : Config) = new Configuration(config)

  /**
   * Reads the configuration from the given string
   * @param s a string in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(s : String) = new Configuration(ConfigFactory.parseReader(new StringReader(s)))

  /**
   * Reads the configuration from the given Reader
   * @param reader reader for a file or stream in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(reader : Reader) = new Configuration(ConfigFactory.parseReader(reader))

  /**
   * Initializes with a java Map, where the values may be other java Maps
   */
  def apply(map : java.util.Map[java.lang.String, java.lang.Object]) = new Configuration(ConfigFactory.parseMap(map))

  /**
   * Initializes with a scala Map, where the values may be other scala or java Maps
   */
  def apply(map : Map[String, Any]) = new Configuration(ConfigFactory.parseMap(toJavaMap(map)))

  /**
   * Reads the configuration from the given file
   * @param file a file in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(file : File) {
    val reader = new FileReader(file)
    try {
      new Configuration(ConfigFactory.parseReader(reader))
    } finally {
      reader.close()
    }
  }

  // Converts a scala.Map to a java.util.Map recursively
  private def toJavaMap(map: Map[String, Any]): java.util.Map[java.lang.String, java.lang.Object] = {
    map.mapValues {
      case subMap: Map[_, _] => toJavaMap(subMap.asInstanceOf[Map[String, Any]])
      case list: List[_] => list.asJava
      case array: Array[_] => array.toList.asJava
      case x => x
    }.asJava.asInstanceOf[java.util.Map[java.lang.String, java.lang.Object]]
  }
}

/**
 * Represents a telescope configuration
 */
class Configuration(config : Config) {
  def root() = new Configuration(config.root.toConfig)
  def rootKey() = config.root().keySet().iterator().next()
  def getConfig(path: String) = new Configuration(config.getConfig(path))
  def size() : Int = config.root().size()
  def hasPath(path: String) = config.hasPath(path)
  def isEmpty: Boolean = config.isEmpty
  def getBoolean(path: String) = config.getBoolean(path)
  def getNumber(path: String) = config.getNumber(path)
  def getInt(path: String) = config.getInt(path)
  def getLong(path: String) = config.getLong(path)
  def getDouble(path: String) = config.getDouble(path)
  def getString(path: String) = config.getString(path)
  def getBytes(path: String) = config.getBytes(path)
  def getMilliseconds(path: String) = config.getMilliseconds(path)
  def getNanoseconds(path: String) = config.getNanoseconds(path)
  def getBooleanList(path: String) = config.getBooleanList(path)
  def getNumberList(path: String) = config.getNumberList(path)
  def getIntList(path: String) = config.getIntList(path)
  def getLongList(path: String) = config.getLongList(path)
  def getDoubleList(path: String) = config.getDoubleList(path)
  def getStringList(path: String) = config.getStringList(path)
  def getAnyRefList(path: String) = config.getAnyRefList(path)
  def getBytesList(path: String) = config.getBytesList(path)
  def getMillisecondsList(path: String) = config.getMillisecondsList(path)
  def getNanosecondsList(path: String) = config.getNanosecondsList(path)
  def format() = config.root.render(Configuration.formatOptions)
  def asMap(path: String) = config.getConfig(path).root().unwrapped()
  override def toString = config.root.render(Configuration.toStringOptions)

  /**
   * Returns a new Configuration with the given path set to the given value
   */
  def withValue(path: String, value: String) : Configuration = {
    Configuration(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Configuration with the given path set to the given value
   */
  def withValue(path: String, value: Number) : Configuration = {
    Configuration(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Configuration with the given path set to the given map of values
   */
  def withValue(path: String, value: Map[String, Any]) : Configuration = {
    Configuration(config.withValue(path, ConfigValueFactory.fromMap(Configuration.toJavaMap(value))))
  }

  /**
   * Returns a new Configuration with configId set to the given value
   * in the root.info section (where root is the top level key).
   * For wait configs, the value is put in the top level.
   */
  def withConfigId(configId: Int) : Configuration = {
    val root = rootKey()
    val info = if (rootKey == "wait") "" else ".info"
    withValue(root + info + ".configId", configId)
  }

  /**
   * Returns a new Configuration with obsId set to the given value
   * in the root.info section (where root is the top level key).
   * For wait configs, the value is put in the top level.
   */
  def withObsId(obsId: String) : Configuration = {
    val root = rootKey()
    val info = if (rootKey == "wait") "" else ".info"
    withValue(root + info + ".obsId", obsId)
  }
}

