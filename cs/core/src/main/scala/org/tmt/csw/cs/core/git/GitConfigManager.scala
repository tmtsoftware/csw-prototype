package org.tmt.csw.cs.core.git

import java.io.{FileNotFoundException, IOException, File}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.lib._
import org.eclipse.jgit.treewalk.TreeWalk
import java.util.{Properties, Date}
import org.eclipse.jgit.storage.file.FileRepository
import scalax.io.Resource
import org.tmt.csw.cs.core._
import org.tmt.csw.cs.api._
import aQute.bnd.annotation.component.Component
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode
import org.tmt.csw.cs.core.GitConfigId
import scala.Some
import org.tmt.csw.cs.api.ConfigFileHistory
import org.tmt.csw.cs.api.ConfigFileInfo

/**
 * Used to initialize an instance of GitConfigManager with a given repository directory
 */
object GitConfigManager {

  /**
   * Creates and returns a GitConfigManager instance using the default directory as the
   * local Git repository root (directory containing .git dir) and the default
   * URI as the remote, central Git repository.
   * If the local repository already exists, it is opened, otherwise it is created.
   * An exception is thrown if the remote repository does not exist.
   * <p>
   * The defaults in this case are stored in the config.prop file in this package under resources.
   *
   * @return a new GitConfigManager configured to use the default local and remote repositories
   */
  def apply(): GitConfigManager = {
    val props = new Properties
    props.load(Thread.currentThread.getContextClassLoader.getResourceAsStream("org/tmt/csw/cs/core/git/config.prop"))
    apply(new File(props.getProperty("git-local-repository")), props.getProperty("git-main-repository"))
  }


  /**
   * Creates and returns a GitConfigManager instance using the given directory as the
   * local Git repository root (directory containing .git dir) and the given
   * URI as the remote, central Git repository.
   * If the local repository already exists, it is opened, otherwise it is created.
   * An exception is thrown if the remote repository does not exist.
   *
   * @param gitWorkDir top level directory to use for storing configuration files and the local git repository (under .git)
   * @param remoteRepo the URI of the remote, main repository
   *
   * @return a new GitConfigManager configured to use the given local and remote repositories
   */
  def apply(gitWorkDir: File, remoteRepo: String): GitConfigManager = {
    // Init local repo
    val gitDir = new File(gitWorkDir, ".git")
    if (gitDir.exists()) {
      val git = new Git(new FileRepository(gitDir.getPath))
      trackMaster(git)
      val result = git.pull.call
      if (!result.isSuccessful) throw new IOException(result.toString)
      new GitConfigManager(git)
    } else {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(remoteRepo.toString).call
      new GitConfigManager(git)
    }
  }

  // Sets the master repository (needed for git push/pull commands)
  private def trackMaster(git: Git) {
    git.branchCreate()
      .setName("master")
      .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
      .setStartPoint("origin/master")
      .setForce(true)
      .call()
  }

  /**
   * FOR TESTING: Deletes the contents of the given directory (recursively).
   * This is meant for use by tests that need to always start with an empty Git repository.
   */
  def deleteLocalRepo(dir: File) {
    if (dir.isDirectory) {
      dir.list.foreach {
        filePath =>
          val file = new File(dir, filePath)
          if (file.isDirectory) {
            deleteLocalRepo(file)
          } else {
            file.delete()
          }
      }
      dir.delete()
    }
  }

  /**
   * FOR TESTING: Initializes a bare repository in the given dir
   * @param dir directory to contain the new bare repository
   */
  def initBareRepo(dir: File)  {
    Git.init.setDirectory(dir).setBare(true).call
  }
}

/**
 * Uses JGit to manage versions of configuration files
 */
@Component
class GitConfigManager(val git: Git) extends ConfigManager {

  /**
   * Creates a config file with the given path and data and optional comment.
   * An IOException is thrown if the file already exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  override def create(path: String, configData: ConfigData, comment: String): ConfigId = {
    val file = fileForPath(path)
    if (file.exists()) throw new IOException("File already exists in repository: " + path)
    put(path, configData, comment)
  }

  /**
   * Updates the config file with the given path and data and optional comment.
   * An FileNotFoundException is thrown if the file does not exists.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  override def update(path: String, configData: ConfigData, comment: String): ConfigId = {
    val file = fileForPath(path)
    if (!file.exists()) throw new FileNotFoundException("File not found: " + path)
    put(path, configData, comment)
  }

  /**
   * Creates or updates a config file with the given path and data and optional comment.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  private def put(path: String, configData: ConfigData, comment: String): ConfigId = {
    val file = fileForPath(path)
    writeToFile(file, configData)
    val dirCache = git.add.addFilepattern(path).call()
    git.commit().setMessage(comment).call
    git.push.call()
    GitConfigId(dirCache.getEntry(path).getObjectId.getName)
  }

  /**
   * Returns true if the given path exists and is being managed
   * @param path the configuration path
   * @return true if the file exists
   */
  def exists(path: String): Boolean = {
    fileForPath(path).exists
  }

  /**
   * Deletes the given config file (older versions will still be available)
   *
   * @param path the configuration path
   */
  override def delete(path: String, comment: String = "deleted") {
    if (!exists(path)) {
      throw new FileNotFoundException("Can't delete " + path + " because it does not exist")
    }
    git.rm.addFilepattern(path).call()
    git.commit().setMessage(comment).call
    git.push.call()
  }

  /**
   * Gets and returns the config file stored under the given path.
   *
   * @param path the configuration path
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return an object containing the configuration data, if found
   */
  override def get(path: String, id: Option[ConfigId]): Option[ConfigData] = {

    if (!id.isEmpty) {
      // return the file for the given id
      val objId = ObjectId.fromString(id.get.asInstanceOf[GitConfigId].id)
      Some(new ConfigBytes(git.getRepository.open(objId).getBytes))
    } else {
      // return the latest version of the file (without checking Git)
      Some(new ConfigFile(fileForPath(path)))
    }
  }

  /**
   * Returns a list containing all known configuration files
   * @return a list containing one ConfigFileInfo object for each known config file
   */
  def list(): List[ConfigFileInfo] = {
    val repo = git.getRepository

    // Resolve the revision specification
    val id = repo.resolve("HEAD")

    // Get the commit object for that revision
    val walk = new RevWalk(repo)
    val commit = walk.parseCommit(id)

    // Get the commit's file tree
    val tree = commit.getTree

    val treeWalk = new TreeWalk(repo)
    treeWalk.setRecursive(true)
    treeWalk.addTree(tree)

    var result: List[ConfigFileInfo] = List()
    while (treeWalk.next) {
      val path = treeWalk.getPathString
      val objectId = treeWalk.getObjectId(0).name
      // TODO: Include create comment (history(path)(0).comment)
      // or latest comment (history(path).last.comment)?
      val info = new ConfigFileInfo(path, GitConfigId(objectId), history(path).last.comment)
      result = info :: result
    }

    result
  }

  /**
   * Returns a list of all known versions of a given path
   * @return a list containing one ConfigFileHistory object for each version of path
   */
  def history(path: String): List[ConfigFileHistory] = {
    val logCommand = git.log
      .add(git.getRepository.resolve(Constants.HEAD))
      .addPath(path)

    val it = logCommand.call.iterator()
    var result: List[ConfigFileHistory] = List()
    while (it.hasNext) {
      val revCommit = it.next()
      val tree = revCommit.getTree
      val treeWalk = TreeWalk.forPath(git.getRepository, path, tree)
      if (treeWalk != null) {
        val objectId = treeWalk.getObjectId(0)
        // TODO: Should comments be allowed to contain newlines? Might want to use longMessage?
        val comment = revCommit.getShortMessage
        val time = new Date(revCommit.getCommitTime * 1000L)
        val info = new ConfigFileHistory(GitConfigId(objectId.name), comment, time)
        result = info :: result
      }

    }
    result
  }

  private def fileForPath(path: String): File = {
    new File(git.getRepository.getWorkTree, path)
  }

  private def writeToFile(file: File, configData: ConfigData) {
    Resource.fromFile(file).truncate(0L); // XXX FIXME: according to docs, this should happen below, but does not!
    Resource.fromFile(file).write(configData.getBytes)
  }
}
