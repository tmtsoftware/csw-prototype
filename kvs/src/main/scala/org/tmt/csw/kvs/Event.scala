package org.tmt.csw.kvs

import com.typesafe.config._
import java.io._
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.Some
import com.typesafe.config.ConfigException.WrongType
import scala.concurrent.duration.FiniteDuration

/**
 * Used for building Event instances.
 * (XXX merge with Configuration class from cmd service?)
 */
object Event {
  val toStringOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
  val formatOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)
  val jsonOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(true).setFormatted(false)

  /**
   * Initialize with an existing typesafe Config object
   */
  private def apply(config: Config) = new Event(config)

  /**
   * Reads the Event from the given string
   * @param s a string in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(s: String): Event = apply(ConfigFactory.parseReader(new StringReader(s)))

  /**
   * Reads the Event from the given byte array
   * @param bytes an array of bytes containing the Event (as for example the result of String.getBytes)
   */
  def apply(bytes: Array[Byte]): Event = apply(ConfigFactory.parseReader(new InputStreamReader(new ByteArrayInputStream(bytes))))

  /**
   * Reads the Event from the given Reader
   * @param reader reader for a file or stream in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(reader: Reader): Event = apply(ConfigFactory.parseReader(reader))

  /**
   * Initializes with a java Map, where the values may be Strings, some kind of Number, other java Maps or Lists
   */
  def apply(map: java.util.Map[java.lang.String, java.lang.Object]): Event = apply(ConfigFactory.parseMap(map))

  /**
   * Initializes with a scala Map, where the values may be Strings, some kind of Number, other java Maps or Lists
   */
  def apply(map: Map[String, Any]): Event = apply(ConfigFactory.parseMap(toJavaMap(map)))

  /**
   * Reads the Event from the given file
   * @param file a file in JSON or "human-friendly JSON" format (see HOCON: https://github.com/typesafehub/config)
   */
  def apply(file: File): Unit = {
    val reader = new FileReader(file)
    try {
      new Event(ConfigFactory.parseReader(reader))
    } finally {
      reader.close()
    }
  }

  /**
   * Returns an empty Event
   */
  def apply(): Event = apply(ConfigFactory.empty())


  // Converts a scala.Map to a java.util.Map recursively
  private def toJavaMap(map: Map[String, Any]): java.util.Map[java.lang.String, java.lang.Object] = {
    map.mapValues {
      case subMap: Map[_, _] => toJavaMap(subMap.asInstanceOf[Map[String, Any]])
      case list: List[_] => list.asJava
      case array: Array[_] => array.toList.asJava
      case x => x
    }.asJava.asInstanceOf[java.util.Map[java.lang.String, java.lang.Object]]
  }

  /**
   * Returns the merge of the events in the list
   */
  def merge(events: List[Event]): Event = {
    events match {
      case head :: Nil => head
      case head :: tail => head.merge(merge(tail))
    }
  }
}

/**
 * Represents a telescope Event
 */
class Event private(private val config: Config) extends Serializable {

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
   * Returns the nested Event at the requested path and throws an exception if not found
   */
  def getEvent(path: String): Event =
    Event(
      try {
        config.getConfig(path)
      } catch {
        case e: WrongType =>
          // If path is for a simple key: value, include the key and value in the result
          val ar = path.split('.')
          config.getConfig(ar.init.mkString(".")).withOnlyPath(ar.last)
      }
    )

  /**
   * Returns this event if pathOpt is None, otherwise the event at the given path.
   * @param pathOpt an optional path in this Event
   */
  def getEvent(pathOpt: Option[String]): Event = {
    if (pathOpt.isEmpty) this else getEvent(pathOpt.get)
  }

  /**
   * Returns the number of top level elements in the Event
   */
  def size(): Int = config.root().size()

  /**
   * Returns true if this config contains the given path
   */
  def hasPath(path: String): Boolean = config.hasPath(path)

  /**
   * Returns the union of this Event and the given one.
   */
  def merge(event: Event): Event = Event(config.withFallback(event.config))

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

  def getBooleanList(path: String): List[Boolean] = config.getBooleanList(path).toList.map(b => b: Boolean)

  def getNumberList(path: String): List[Number] = config.getNumberList(path).toList

  def getIntList(path: String): List[Int] = config.getIntList(path).toList.map(i => i: Int)

  def getLongList(path: String): List[Long] = config.getLongList(path).toList.map(l => l: Long)

  def getDoubleList(path: String): List[Double] = config.getDoubleList(path).toList.map(d => d: Double)

  def getStringList(path: String): List[String] = config.getStringList(path).toList

  def getBytesList(path: String): List[Long] = config.getBytesList(path).toList.map(l => l: Long)

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
   * Returns the Event formatted on multiple lines.
   */
  def format(): String = config.root.render(Event.formatOptions)

  /**
   * Returns Event formatted on a single line
   */
  override def toString: String = config.root.render(Event.toStringOptions)

  /**
   * Returns Event formatted on a single line in JSON format
   */
  def toJson: String = config.root.render(Event.jsonOptions)

  /**
   * Returns a new Event with the given path set to the given value
   */
  def withValue(path: String, value: String): Event = {
    new Event(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Event with the given path set to the given value
   */
  def withValue(path: String, value: Number): Event = {
    new Event(config.withValue(path, ConfigValueFactory.fromAnyRef(value)))
  }

  /**
   * Returns a new Event with the given path set to the given map of values
   */
  def withValue(path: String, value: Map[String, Any]): Event = {
    new Event(config.withValue(path, ConfigValueFactory.fromMap(Event.toJavaMap(value))))
  }

  /**
   * Returns a new Event with the given path set to the given list of values
   */
  def withValue(path: String, value: List[AnyRef]): Event = {
    new Event(config.withValue(path, ConfigValueFactory.fromIterable(value.asJavaCollection)))
  }

  /**
   * Clone the event with the given path removed.
   *
   * @param path path to remove
   * @return a copy of the event minus the specified path
   */
  def withoutPath(path: String): Event = {
    new Event(config.withoutPath(path))
  }

  /**
   * Clone the event with only the given path (and its children) retained;
   * all sibling paths are removed.
   *
   * @param path path to keep
   * @return a copy of the event minus all paths except the one specified
   */
  def withOnlyPath(path: String): Event = {
    new Event(config.withOnlyPath(path))
  }

  override def equals(other: Any): Boolean = {
    other match {
      case event: Event =>
        this.config.equals(event.config)
      case _ =>
        false
    }
  }
}

