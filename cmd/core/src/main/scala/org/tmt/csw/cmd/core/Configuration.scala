package org.tmt.csw.cmd.core

import com.typesafe.config.{ConfigRenderOptions, ConfigFactory, Config}
import java.io.StringReader
import scala.collection.JavaConverters._
//import scala.collection.JavaConversions._

object Configuration {
  val toStringOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(false)
  val formatOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)

  def apply(config : Config) = new Configuration(config)
  def apply(s : String) = new Configuration(ConfigFactory.parseReader(new StringReader(s)))
  def apply(map : java.util.Map[java.lang.String, java.lang.Object]) = new Configuration(ConfigFactory.parseMap(map))
  def apply(map : Map[String, AnyRef]) = new Configuration(ConfigFactory.parseMap(toJavaMap(map)))

  def toJavaMap(map: Map[String, AnyRef]): java.util.Map[java.lang.String, java.lang.Object] = {
    map.asJava
  }
}

/**
 * Represents a telescope configuration
 */
class Configuration(config : Config) {
  def root(): Configuration = new Configuration(config.root.toConfig)
  def getConfig(path: String) = new Configuration(config.getConfig(path))
  def size() : Int = config.root().size()
  def hasPath(path: String): Boolean = config.hasPath(path)
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
  def getBooleanList(path: String): List[Boolean] = config.getBooleanList(path).asInstanceOf[List[Boolean]]
  def getNumberList(path: String): List[Number] = config.getNumberList(path).asInstanceOf[List[Number]]
  def getIntList(path: String): List[Int] = config.getIntList(path).asInstanceOf[List[Int]]
  def getLongList(path: String): List[Long] = config.getLongList(path).asInstanceOf[List[Long]]
  def getDoubleList(path: String): List[Double] = config.getDoubleList(path).asInstanceOf[List[Double]]
  def getStringList(path: String): List[String] = config.getStringList(path).asInstanceOf[List[String]]
  def getAnyRefList(path: String): List[_ <: AnyRef] = config.getAnyRefList(path).asInstanceOf[List[AnyRef]]
  def getBytesList(path: String): List[Long] = config.getBytesList(path).asInstanceOf[List[Long]]
  def getMillisecondsList(path: String): List[Long] = config.getMillisecondsList(path).asInstanceOf[List[Long]]
  def getNanosecondsList(path: String): List[Long] = config.getNanosecondsList(path).asInstanceOf[List[Long]]
  def format : String = config.root.render(Configuration.formatOptions)
  override def toString : String = config.root.render(Configuration.toStringOptions)
}
