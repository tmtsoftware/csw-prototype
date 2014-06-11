package csw.services.cs.core.git

import java.util
import java.io.File
import scala.collection.JavaConversions._
import scala.Some
import java.net.URI
import csw.services.cs.core.{ConfigData, ConfigId, ConfigFileInfo, ConfigFileHistory}
import csw.services.cs.JConfigManager

/**
 * Uses JGit to manage versions of configuration files.
 *
 * Note: This version is for use by Java applications. Scala applications should use
 * [[csw.services.cs.core.git.GitConfigManager]].
 */
class JGitConfigManager(gitWorkDir: File, remoteRepo: URI) extends JConfigManager {
  private val manager = GitConfigManager(gitWorkDir, remoteRepo)

  override def create(path: File, configData: ConfigData, comment: String): ConfigId = {
    manager.create(path, configData, comment)
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
