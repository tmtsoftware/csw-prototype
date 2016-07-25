package javacsw.services.cs.core

import java.io.File
import java.util.{Date, Optional}
import java.{lang, util}
import javacsw.services.cs.{IBlockingConfigData, IBlockingConfigManager}

import akka.actor.ActorRefFactory
import csw.services.cs.core.{ConfigFileHistory, _}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A blocking Java API for the config service.
 */
case class JBlockingConfigManager(manager: ConfigManager)(implicit context: ActorRefFactory)
    extends IBlockingConfigManager {

  import context.dispatcher

  val timeout: Duration = 30.seconds

  override def create(path: File, configData: ConfigData, oversize: lang.Boolean, comment: String): ConfigId = {
    Await.result(manager.create(path, configData, oversize, comment), timeout)
  }

  override def update(path: File, configData: ConfigData, comment: String): ConfigId = {
    Await.result(manager.update(path, configData, comment), timeout)
  }

  override def createOrUpdate(path: File, configData: ConfigData, oversize: lang.Boolean, comment: String): ConfigId = {
    Await.result(manager.createOrUpdate(path, configData, oversize, comment), timeout)
  }

  override def get(path: File): Optional[IBlockingConfigData] = {
    Await.result(manager.get(path), timeout).map { c =>
      val result: IBlockingConfigData = JBlockingConfigData(c)
      result
    }.asJava
  }

  override def get(path: File, id: ConfigId): Optional[IBlockingConfigData] = {
    Await.result(manager.get(path, Some(id)), timeout).map { c =>
      val result: IBlockingConfigData = JBlockingConfigData(c)
      result
    }.asJava
  }

  override def get(path: File, date: Date): Optional[IBlockingConfigData] = {
    Await.result(manager.get(path, date), timeout).map { c =>
      val result: IBlockingConfigData = JBlockingConfigData(c)
      result
    }.asJava
  }

  override def exists(path: File): Boolean = {
    Await.result(manager.exists(path), timeout)
  }

  override def delete(path: File): Unit = {
    Await.result(manager.delete(path), timeout)
  }

  override def delete(path: File, comment: String): Unit = {
    Await.result(manager.delete(path, comment), timeout)
  }

  override def list(): util.List[ConfigFileInfo] = {
    Await.result(manager.list().map(_.asJava), timeout)
  }

  override def history(path: File): util.List[ConfigFileHistory] = {
    Await.result(manager.history(path).map(_.asJava), timeout)
  }

  /**
   * Sets the "default version" of the file with the given path.
   * If this method is not called, the default version will always be the latest version.
   * After calling this method, the version with the given Id will be the default.
   *
   * @param path the file path relative to the repository root
   * @param id an optional id used to specify a specific version
   *           (by default the id of the latest version is used)
   * @return an result
   */
  override def setDefault(path: File, id: Optional[ConfigId]): Unit =
    Await.result(manager.setDefault(path, id.asScala), timeout)

  /**
   * Resets the "default version" of the file with the given path to be always the latest version.
   *
   * @param path the file path relative to the repository root
   */
  override def resetDefault(path: File): Unit =
    Await.result(manager.resetDefault(path), timeout)

  /**
   * Gets and returns the default version of the file stored under the given path.
   * If no default was set, this returns the latest version.
   *
   * @param path the file path relative to the repository root
   * @return an object that can be used to access the file's data, if found
   */
  override def getDefault(path: File): Optional[ConfigData] =
    Await.result(manager.getDefault(path), timeout).asJava

}

case class JBlockingConfigData(configData: ConfigData)(implicit context: ActorRefFactory) extends IBlockingConfigData {
  private val timeout = 30.seconds

  override def toString: String = Await.result(configData.toFutureString, timeout)

  override def writeToFile(file: File): Unit = Await.result(configData.writeToFile(file), timeout)
}

