package org.tmt.csw.cmd.core

import com.typesafe.config._
import java.io._
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import java.util.UUID
import scala.Some

/**
 * Used for building Configuration instances.
 */
object Configuration {
  val toStringOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
  val formatOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)
  val jsonOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(true).setFormatted(false)

  def waitConfig(forResume: Boolean, obsId: String): Configuration = {
    Configuration(Map("wait" -> Map("forResume" -> forResume, "obsId" -> obsId)))
  }

  /**
   * Initialize with an existing typesafe Config object
   */
  private def apply(config: Config) = new Configuration(config).withConfigId(UUID.randomUUID().toString)

  /**
   * Reads the configuration from the given string
   * @param s a string in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(s: String): Configuration = apply(ConfigFactory.parseReader(new StringReader(s)))

  /**
   * Reads the configuration from the given byte array
   * @param bytes an array of bytes containing the configuration (as for example the result of String.getBytes)
   */
  def apply(bytes: Array[Byte]): Configuration = apply(ConfigFactory.parseReader(new InputStreamReader(new ByteArrayInputStream(bytes))))

  /**
   * Reads the configuration from the given Reader
   * @param reader reader for a file or stream in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(reader: Reader): Configuration = apply(ConfigFactory.parseReader(reader))

  /**
   * Initializes with a java Map, where the values may be Strings, some kind of Number, other java Maps or Lists
   */
  def apply(map: java.util.Map[java.lang.String, java.lang.Object]): Configuration = apply(ConfigFactory.parseMap(map))

  /**
   * Initializes with a scala Map, where the values may be Strings, some kind of Number, other java Maps or Lists
   */
  def apply(map: Map[String, Any]): Configuration = apply(ConfigFactory.parseMap(toJavaMap(map)))

  /**
   * Reads the configuration from the given file
   * @param file a file in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(file: File): Unit = {
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
class Configuration private(private val config: Config) extends Serializable {

//  println(s"XXX Configuration: config = $config")

  /**
   * Returns
   */
  def root(): Configuration = new Configuration(config.root.toConfig)

  /**
   * Returns the set of root keys
   */
  def rootKeys(): Set[String] = config.root().keySet().asScala.toSet

  /**
   * Returns the root key, if there is exactly one, otherwise None
   */
  def rootKey(): Option[String] = {
    val rootKeys = config.root().keySet()
    if (rootKeys.size == 1) Some(rootKeys.iterator().next()) else None
  }

  /**
   * Returns the nested Configuration at the requested path and throws an exception if not found
   */
  def getConfig(path: String): Configuration = Configuration(config.getConfig(path))

  /**
   * Returns the number of top level elements in the configuration
   */
  def size(): Int = config.root().size()

  /**
   * Returns true if this config contains the given path
   */
  def hasPath(path: String): Boolean = config.hasPath(path)

  /**
   * Returns the union of this configuration and the given one.
   */
  def merge(c2: Configuration): Configuration = Configuration(config.withFallback(c2.config))

  /**
   * Returns true if this config is empty
   */
  def isEmpty: Boolean = config.isEmpty

  def getBoolean(path: String): Boolean = config.getBoolean(path)

  def getNumber(path: String): Number = config.getNumber(path)

  def getInt(path: String): Int = config.getInt(path)

  def getLong(path: String): Long = config.getLong(path)

  def getDouble(path: String): Double = config.getDouble(path)

  def getString(path: String): String = config.getString(path)

  def getBytes(path: String): Long = config.getBytes(path)

  def getMilliseconds(path: String): Long = config.getMilliseconds(path)

  def getNanoseconds(path: String): Long = config.getNanoseconds(path)

  def getBooleanList(path: String): List[Boolean] = config.getBooleanList(path).toList.map(b => b: Boolean)

  def getNumberList(path: String): List[Number] = config.getNumberList(path).toList

  def getIntList(path: String): List[Int] = config.getIntList(path).toList.map(i => i: Int)

  def getLongList(path: String): List[Long] = config.getLongList(path).toList.map(l => l: Long)

  def getDoubleList(path: String): List[Double] = config.getDoubleList(path).toList.map(d => d: Double)

  def getStringList(path: String): List[String] = config.getStringList(path).toList

  def getBytesList(path: String): List[Long] = config.getBytesList(path).toList.map(l => l: Long)

  def getMillisecondsList(path: String): List[Long] = config.getMillisecondsList(path).toList.map(l => l: Long)

  def getNanosecondsList(path: String): List[Long] = config.getNanosecondsList(path).toList.map(l => l: Long)

  /**
   * Returns a Map containing the contents of this object at the given path.
   */
  def asMap(path: String = ""): Map[String, AnyRef] =
    if (path == "") {
      config.root().unwrapped().toMap
    } else {
      config.getConfig(path).root().unwrapped().toMap
    }

  /**
   * Returns the configuration formatted on multiple lines.
   */
  def format(): String = config.root.render(Configuration.formatOptions)

  /**
   * Returns configuration formatted on a single line
   */
  override def toString: String = config.root.render(Configuration.toStringOptions)

  /**
   * Returns configuration formatted on a single line in JSON format
   */
  def toJson: String = config.root.render(Configuration.jsonOptions)

  /**
   * Returns the config with the "info" section removed (for testing)
   */
  private def withoutInfo(): Configuration = {
    new Configuration(config.withoutPath(infoPath()))
  }

  /**
   * Returns configuration formatted on a single line, but not including the info section (with generated configId)
   * (For use in test cases where the unique configId makes it difficult to compare results.)
   */
  def toTestString: String = withoutInfo().toString()

  /**
   * Returns a new Configuration with the given path set to the given value
   */
  def withValue(path: String, value: String): Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Configuration with the given path set to the given value
   */
  def withValue(path: String, value: Number): Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Configuration with the given path set to the given map of values
   */
  def withValue(path: String, value: Map[String, Any]): Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromMap(Configuration.toJavaMap(value))))
  }

  /**
   * Returns a new Configuration with the given path set to the given list of values
   */
  def withValue(path: String, value: List[AnyRef]): Configuration = {
    new Configuration(config.withValue(path, ConfigValueFactory.fromIterable(value.asJavaCollection)))
  }

  /**
   * Clone the config with the given path removed.
   *
   * @param path path to remove
   * @return a copy of the config minus the specified path
   */
  def withoutPath(path: String): Configuration = {
    new Configuration(config.withoutPath(path))
  }

  /**
   * Clone the config with only the given path (and its children) retained;
   * all sibling paths are removed.
   *
   * @param path path to keep
   * @return a copy of the config minus all paths except the one specified
   */
  def withOnlyPath(path: String): Configuration = {
    new Configuration(config.withOnlyPath(path))
  }

  // Path to section containing configId and obsId
  private def infoPath(): String = {
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
  private def configIdPath(): String = {
    infoPath() + ".configId"
  }

  /**
   * Returns a new Configuration with configId set to the given value
   * in the root.info section (where root is the top level key).
   * For wait configs, the value is put in the top level.
   */
  def withConfigId(configId: String): Configuration = {
    withValue(configIdPath(), configId)
  }

  /**
   * Returns the automatically added configId
   */
  def getConfigId: String = getString(configIdPath())

  // Path to the obsId item
  private def obsIdPath(): String = {
    infoPath() + ".obsId"
  }

  /**
   * Returns a new Configuration with obsId set to the given value
   * in the root.info section (where root is the top level key).
   * For wait configs, the value is put in the top level.
   */
  def withObsId(obsId: String): Configuration = {
    withValue(obsIdPath(), obsId)
  }

  /**
   * Returns the obsId
   */
  def getObsId: String = getString(obsIdPath())

  /**
   * Returns true if this is a wait config
   */
  def isWaitConfig: Boolean = {
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

