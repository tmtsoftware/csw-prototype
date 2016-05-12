package javacsw.services.cs.core

import java.io.File
import java.util.Optional
import java.{lang, util}
import javacsw.services.cs.{JBlockingConfigData, JBlockingConfigManager}

import akka.actor.ActorRefFactory
import csw.services.cs.core.{ConfigFileHistory, _}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Java API for the config service.
 */
case class JBlockingConfigManagerImpl(manager: ConfigManager)(implicit context: ActorRefFactory)
    extends JBlockingConfigManager {

  import context.dispatcher

  private val timeout = 30.seconds // XXX Should this be a parameter?

  override def create(path: File, configData: ConfigData, oversize: lang.Boolean, comment: String): ConfigId = {
    Await.result(manager.create(path, configData, oversize, comment), timeout)
  }

  override def update(path: File, configData: ConfigData, comment: String): ConfigId = {
    Await.result(manager.update(path, configData, comment), timeout)
  }

  override def createOrUpdate(path: File, configData: ConfigData, oversize: lang.Boolean, comment: String): ConfigId = {
    Await.result(manager.createOrUpdate(path, configData, oversize, comment), timeout)
  }

  override def get(path: File): Optional[JBlockingConfigData] = {
    Await.result(manager.get(path), timeout).map {c =>
      val result: JBlockingConfigData = JBlockingConfigDataImpl(c)
      result
    }.asJava
  }

  override def get(path: File, id: ConfigId): Optional[JBlockingConfigData] = {
    Await.result(manager.get(path, Some(id)), timeout).map {c =>
      val result: JBlockingConfigData = JBlockingConfigDataImpl(c)
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
}

case class JBlockingConfigDataImpl(configData: ConfigData)(implicit context: ActorRefFactory) extends JBlockingConfigData {
  private val timeout = 30.seconds

  override def toString: String = Await.result(configData.toFutureString, timeout)

  override def writeToFile(file: File): Unit = Await.result(configData.writeToFile(file), timeout)
}

