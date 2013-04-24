package org.tmt.csw.cs.git

import java.io.{FileWriter, File}
import org.tmt.csw.cs.{ConfigBytes, ConfigFile, ConfigData, ConfigManager}
import org.eclipse.jgit.api.Git
import scalax.io.Resource
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.treewalk.TreeWalk

/**
 * Used to initialize an instance of GitConfigManager with a given repository directory
 */
object GitConfigManager {

  /**
   * Initializes with the directory path of the existing Git repository to use to store the configuration files.
   * Can be called like this, for example: def configManager = GitConfigManager(new File("/my/dir"))
   *
   * @param gitWorkDir top level directory to use for storing configuration files and the local git repository (under .git)
   * @param create if true, the Git repository is created (if it does not exist)
   *
   * @return a new GitConfigManager configured to use the given directory
   */
  def apply(gitWorkDir: File, create: Boolean = false): GitConfigManager = {
    if (create && !gitWorkDir.exists()) {
      new GitConfigManager(Git.init().setDirectory(gitWorkDir).call())
    } else {
      new GitConfigManager(Git.open(gitWorkDir))
    }
  }

  /**
   * Deletes the contents of the given directory (recursively).
   * This is meant for use by tests that need to always start with an empty Git repository.
   * @param dir directory to delete
   */
  def delete(dir: File) {
    if (dir.isDirectory()) {
      dir.list().foreach {
        filePath =>
          val file = new File(dir, filePath)
          if (file.isDirectory()) {
            delete(file)
          } else {
            file.delete()
          }
      }
      dir.delete()
    }
  }
}

/**
 * Uses JGit to manage versions of configuration files
 */
class GitConfigManager(val git: Git) extends ConfigManager {

  /**
   * Creates or updates a config file with the given path and data and optional comment.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  def put(path: String, configData: ConfigData, comment: String): String = {
    val file = fileForPath(path)
    writeToFile(file, configData)
    val dirCache = git.add.addFilepattern(path).call()
    git.commit().setMessage(comment).call
    dirCache.getEntry(path).getObjectId.getName
//    idForPathInHead(path).get
  }

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   * @param comment an optional comment
   */
  def delete(path: String, configData: ConfigData, comment: String) {
    // TODO
  }

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return an object containing the configuration data, if found
   */
  def get(path: String, id: Option[String]): Option[ConfigData] = {

    if (!id.isEmpty) {
      // return the file for the given id
      val objId = ObjectId.fromString(id.get)
      Some(new ConfigBytes(git.getRepository.open(objId).getBytes))
    } else {
      // return the latest version of the file (without checking Git)
      Some(new ConfigFile(fileForPath(path)))
    }
  }

  /**
   * Returns a list containing all known configuration files
   */
  def list(): List[String] = {
    // TODO
    List()
  }

  /**
   * Returns a list of tuples (id, comment) containing all known version ids and the associated comments
   * for the given configuration path
   *
   * @param path the configuration path
   * @return a list containing one tuple (id, comment) for each version of the given configuration path
   */
  def versions(path: String): List[(String, String)] = {
    // TODO
    List()
  }


  private def fileForPath(path: String): File = {
    new File(git.getRepository.getWorkTree, path)
  }

  private def writeToFile(file: File, configData: ConfigData) {
    Resource.fromFile(file).write(configData.getBytes)
  }

//  private def idForPathInHead(path: String): Option[String] = {
//    val repo = git.getRepository
//
//    // Resolve the revision specification
//    val id = repo.resolve("HEAD")
//
//    // Get the commit object for that revision
//    val walk = new RevWalk(repo)
//    val commit = walk.parseCommit(id)
//
//    // Get the commit's file tree
//    val tree = commit.getTree()
//
//    // .. and narrow it down to the single file's path
//    val treewalk = TreeWalk.forPath(repo, path, tree)
//
//    if (treewalk != null) {
//      // if the file exists in that commit
//      // use the blob id to read the file's data
//      Some(treewalk.getObjectId(0).getName)
//    } else {
//      None
//    }
//  }

}
