package org.tmt.csw.cs.core.git

import org.tmt.csw.cs.api._
import java.util
import java.io.File
import scala.collection.JavaConversions._
import org.tmt.csw.cs.api.ConfigFileHistory
import org.tmt.csw.cs.api.ConfigFileInfo
import scala.Some
import java.net.URI

/**
 * Uses JGit to manage versions of configuration files.
 *
 * Note: This version is for use by Java applications. Scala applications should use
 * [[org.tmt.csw.cs.core.git.GitConfigManager]].
 */
class JGitConfigManager(gitWorkDir: File, remoteRepo: URI) extends JConfigManager {
  private val manager = GitConfigManager(gitWorkDir, remoteRepo)

  override def create(path: String, configData: ConfigData, comment: String): ConfigId = {
    manager.create(path, configData, comment)
  }

  override def update(path: String, configData: ConfigData, comment: String): ConfigId = {
    manager.update(path, configData, comment)
  }

  override def get(path: String): ConfigData = {
    manager.get(path).orNull
  }

  override def get(path: String, id: ConfigId): ConfigData = {
    manager.get(path, Some(id)).orNull
  }

  override def exists(path: String): Boolean = {
    manager.exists(path)
  }

  override def delete(path: String): Unit = {
    manager.delete(path)
  }

  override def delete(path: String, comment: String): Unit = {
    manager.delete(path, comment)
  }

  override def list(): util.List[ConfigFileInfo] = {
    manager.list()
  }

  override def history(path: String): util.List[ConfigFileHistory] = {
    manager.history(path)
  }
}
