package csw.services.cs.core.git

import java.io.File
import java.net.URI
import java.{lang, util}

import csw.services.cs.JConfigManager
import csw.services.cs.core.{ConfigData, ConfigFileHistory, ConfigFileInfo, ConfigId}

import scala.collection.JavaConversions._

/**
 * Uses JGit to manage versions of configuration files.
 *
 * Note: This version is for use by Java applications. Scala applications should use
 * [[csw.services.cs.core.git.GitConfigManager]].
 */
case class JGitConfigManager(gitWorkDir: File, remoteRepo: URI, gitOversizeStorage: URI) extends JConfigManager {
  private val manager = GitConfigManager(gitWorkDir, remoteRepo, gitOversizeStorage)

  override def create(path: File, configData: ConfigData, oversize: lang.Boolean, comment: String): ConfigId = {
    manager.create(path, configData, oversize, comment)
  }

  override def update(path: File, configData: ConfigData, comment: String): ConfigId = {
    manager.update(path, configData, comment)
  }

  override def get(path: File): ConfigData = {
    manager.get(path).orNull
  }

  override def get(path: File, id: ConfigId): ConfigData = {
    manager.get(path, Some(id)).orNull
  }

  override def exists(path: File): Boolean = {
    manager.exists(path)
  }

  override def delete(path: File): Unit = {
    manager.delete(path)
  }

  override def delete(path: File, comment: String): Unit = {
    manager.delete(path, comment)
  }

  override def list(): util.List[ConfigFileInfo] = {
    manager.list()
  }

  override def history(path: File): util.List[ConfigFileHistory] = {
    manager.history(path)
  }
}
