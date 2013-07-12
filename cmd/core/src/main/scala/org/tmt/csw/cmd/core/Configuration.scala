package org.tmt.csw.cmd.core

import com.typesafe.config._
import java.io._
import scala.collection.JavaConverters._
import java.util.UUID
import scala.Some

/**
 * Used for building Configuration instances.
 */
object Configuration {
  val toStringOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
  val formatOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)

  def waitConfig(forResume: Boolean, obsId: String) : Configuration = {
    Configuration(Map("wait" -> Map("forResume" -> forResume, "obsId" -> obsId)))
  }

  /**
   * Initialize with an existing typesafe Config object
   */
  private def apply(config : Config) = new Configuration(config).withConfigId(UUID.randomUUID().toString)

  /**
   * Reads the configuration from the given string
   * @param s a string in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(s : String): Configuration = apply(ConfigFactory.parseReader(new StringReader(s)))

  /**
   * Reads the configuration from the given byte array
   * @param bytes an array of bytes containing the configuration (as for example the result of String.getBytes)
   */
  def apply(bytes : Array[Byte]): Configuration = apply(ConfigFactory.parseReader(new InputStreamReader(new ByteArrayInputStream(bytes))))

  /**
   * Reads the configuration from the given Reader
   * @param reader reader for a file or stream in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(reader : Reader): Configuration = apply(ConfigFactory.parseReader(reader))

  /**
   * Initializes with a java Map, where the values may be Strings, some kind of Number, other java Maps or Lists
   */
  def apply(map : java.util.Map[java.lang.String, java.lang.Object]): Configuration = apply(ConfigFactory.parseMap(map))

  /**
   * Initializes with a scala Map, where the values may be Strings, some kind of Number, other java Maps or Lists
   */
  def apply(map : Map[String, Any]): Configuration = apply(ConfigFactory.parseMap(toJavaMap(map)))

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
class Configuration private (private val config : Config) extends Serializable {
  /**
   * Returns
   */
  def root() = new Configuration(config.root.toConfig)

  /**
   * Returns the set of root keys
   */
  def rootKeys() = config.root().keySet()

  /**
   * Returns the root key, if there is exactly one, otherwise None
   */
  def rootKey() : Option[String] = {
    val rootKeys = config.root().keySet()
    if (rootKeys.size == 1) Some(rootKeys.iterator().next()) else None
  }

  /**
   * Returns the nested Configuration at the requested path and throws an exception if not found
   */
  def getConfig(path: String) = new Configuration(config.getConfig(path))

  /**
   * Returns the number of top level elements in the configuration
   */
  def size() : Int = config.root().size()

  /**
   * Returns true if this config contains the given path
   */
  def hasPath(path: String) = config.hasPath(path)


  /**
   * Returns true if this config is empty
   */
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

  /**
   * Returns a Map containing the contents of this object at the given path.
   */
  def asMap(path: String) = config.getConfig(path).root().unwrapped()

  /**
   * Returns the configuration formatted on multiple lines.
   */
  def format() = config.root.render(Configuration.formatOptions)

  /**
   * Returns configuration formatted on a single line
   */
  override def toString = config.root.render(Configuration.toStringOptions)

  /**
   * Returns the config with the "info" section removed (for testing)
   */
  private def withoutInfo() : Configuration = {
    new Configuration(config.withoutPath(infoPath()))
  }

  /**
   * Returns configuration formatted on a single line, but not including the info section (with generated configId)
   * (For use in test cases where the unique configId makes it difficult to compare results.)
   */
  def toTestString = withoutInfo().toString()

  /**
   * Returns a new Configuration with the given path set to the given value
   */
  def withValue(path: String, value: String) : Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Configuration with the given path set to the given value
   */
  def withValue(path: String, value: Number) : Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Configuration with the given path set to the given map of values
   */
  def withValue(path: String, value: Map[String, Any]) : Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromMap(Configuration.toJavaMap(value))))
  }

  /**
   * Returns a new Configuration with the given path set to the given list of values
   */
  def withValue(path: String, value: List[AnyRef]) : Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromIterable(value.asJavaCollection)))
  }

  /**
   * Clone the config with the given path removed.
   *
   * @param path path to remove
   * @return a copy of the config minus the specified path
   */
  def withoutPath(path: String) : Configuration = {
    new Configuration(config.withoutPath(path))
  }

  /**
   * Clone the config with only the given path (and its children) retained;
   * all sibling paths are removed.
   *
   * @param path path to keep
   * @return a copy of the config minus all paths except the one specified
   */
  def withOnlyPath(path: String) : Configuration = {
    new Configuration(config.withOnlyPath(path))
  }

  // Path to section containing configId and obsId
  private def infoPath() : String = {
    val root = rootKey().getOrElse("")
    if (isWaitConfig) {
      root
    } else if (root != "") {
      root + ".info"
    } else {
      "info"
    }
  }

  // Path to the configId item, which is automatically added to all Configurations.
  private def configIdPath() : String = {
    infoPath() + ".configId"
  }

  /**
   * Returns a new Configuration with configId set to the given value
   * in the root.info section (where root is the top level key).
   * For wait configs, the value is put in the top level.
   */
  def withConfigId(configId: String) : Configuration = {
    withValue(configIdPath(), configId)
  }

  /**
   * Returns the automatically added configId
   */
  def getConfigId = getString(configIdPath())

  // Path to the obsId item
  private def obsIdPath() : String = {
    infoPath() + ".obsId"
  }

  /**
   * Returns a new Configuration with obsId set to the given value
   * in the root.info section (where root is the top level key).
   * For wait configs, the value is put in the top level.
   */
  def withObsId(obsId: String) : Configuration = {
    withValue(obsIdPath(), obsId)
  }

  /**
   * Returns the obsId
   */
  def getObsId = getString(obsIdPath())

  /**
   * Returns true if this is a wait config
   */
  def isWaitConfig : Boolean = {
    rootKey().getOrElse("") == "wait"
  }

  override def hashCode(): Int = config.hashCode()

  override def equals(other: Any): Boolean = {
    other match {
      case configuration: Configuration =>
        this.config.equals(configuration.config)
      case _ =>
        false
    }
  }
}

