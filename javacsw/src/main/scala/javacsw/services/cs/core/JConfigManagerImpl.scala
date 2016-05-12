package javacsw.services.cs.core

import java.io.File
import java.util.Optional
import java.util.concurrent.CompletableFuture
import javacsw.services.cs.{JConfigData, JConfigManager}

import akka.actor.ActorRefFactory
import csw.services.cs.core.{ConfigFileHistory, _}

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

/**
 * Java API for the config service.
 */
case class JConfigManagerImpl(manager: ConfigManager)(implicit context: ActorRefFactory)
    extends JConfigManager {

  import context.dispatcher

  override def create(path: File, configData: ConfigData, oversize: java.lang.Boolean, comment: String): CompletableFuture[ConfigId] =
    manager.create(path, configData, oversize, comment).toJava.toCompletableFuture

  override def update(path: File, configData: ConfigData, comment: String): CompletableFuture[ConfigId] =
    manager.update(path, configData, comment).toJava.toCompletableFuture

  override def createOrUpdate(path: File, configData: ConfigData, oversize: java.lang.Boolean, comment: String): CompletableFuture[ConfigId] =
    manager.createOrUpdate(path, configData, oversize, comment).toJava.toCompletableFuture

  override def get(path: File): CompletableFuture[Optional[JConfigData]] =
    // Note: First map is for the future, second to convert scala Option to java Optional
    manager.get(path).map(_.map { c ⇒
      val result: JConfigData = JConfigDataImpl(c)
      result
    }.asJava).toJava.toCompletableFuture

  override def get(path: File, id: ConfigId): CompletableFuture[Optional[JConfigData]] =
    // Note: First map is for the future, second to convert scala Option to java Optional
    manager.get(path, Some(id)).map(_.map { c ⇒
      val result: JConfigData = JConfigDataImpl(c)
      result
    }.asJava).toJava.toCompletableFuture

  override def exists(path: File): CompletableFuture[java.lang.Boolean] =
    manager.exists(path).map(Boolean.box).toJava.toCompletableFuture

  override def delete(path: File): CompletableFuture[Unit] =
    manager.delete(path).toJava.toCompletableFuture

  override def delete(path: File, comment: String): CompletableFuture[Unit] =
    manager.delete(path, comment).toJava.toCompletableFuture

  override def list(): CompletableFuture[java.util.List[ConfigFileInfo]] =
    // Note: map converts scala list to java list
    manager.list().map(_.asJava).toJava.toCompletableFuture

  override def history(path: File): CompletableFuture[java.util.List[ConfigFileHistory]] =
    manager.history(path).map(_.asJava).toJava.toCompletableFuture
}

case class JConfigDataImpl(configData: ConfigData)(implicit context: ActorRefFactory) extends JConfigData {
  // XXX TODO: Add static factory methods

  override def toFutureString: CompletableFuture[String] = configData.toFutureString.toJava.toCompletableFuture

  override def writeToFile(file: File): CompletableFuture[Unit] = configData.writeToFile(file).toJava.toCompletableFuture
}

