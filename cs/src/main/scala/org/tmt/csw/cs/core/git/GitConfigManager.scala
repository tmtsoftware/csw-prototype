package org.tmt.csw.cs.core.git

import java.io.{FileNotFoundException, IOException, File}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.lib._
import org.eclipse.jgit.treewalk.TreeWalk
import java.util.Date
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import scalax.io.Resource
import org.tmt.csw.cs.core._
import org.tmt.csw.cs.core.GitConfigId
import com.typesafe.scalalogging.slf4j.Logging
import scala.annotation.tailrec
import java.net.URI
import java.nio.file.Files

/**
 * Used to initialize an instance of GitConfigManager with a given repository directory
 */
object GitConfigManager {

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
  def apply(gitWorkDir: File, remoteRepo: URI): GitConfigManager = {
    // Init local repo
    val gitDir = new File(gitWorkDir, ".git")
    if (gitDir.exists()) {
      val git = new Git(new FileRepositoryBuilder().setGitDir(gitDir).build())
      val result = git.pull.call
      if (!result.isSuccessful) throw new IOException(result.toString)
      new GitConfigManager(git)
    } else {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(remoteRepo.toString).call
      new GitConfigManager(git)
    }
  }

  /**
   * FOR TESTING: Deletes the contents of the given directory (recursively).
   * This is meant for use by tests that need to always start with an empty Git repository.
   */
  def deleteLocalRepo(dir: File): Unit = {
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
   * Initializes a bare repository in the given dir
   * (A bare repository is one containing only the .git dir and no checked out files).
   *
   * Note: We need to add at least one file after creating the main repository to avoid errors
   * later when pulling from an empty repo. Somehow adding the file also initializes
   * the master branch. The problem shows up when multiple clients create an instance
   * of GitConfigManager and try to pull from an empty, newly created git repo.
   *
   * @param dir directory to contain the new bare repository
   */
  def initBareRepo(dir: File): Unit = {
    // Create the new main repo
    Git.init.setDirectory(dir).setBare(true).call

    // Add a README file to a temporary clone of the main repo and push it to the new repo.
    val tmpDir = Files.createTempDirectory("TempConfigServiceRepo").toFile
    val gm = GitConfigManager(tmpDir, dir.toURI)
    try {
      gm.create(new File("README"), ConfigString("This is the main Config Service Git repository."))
    } finally {
      deleteLocalRepo(tmpDir)
    }
  }
}

/**
 * Uses JGit to manage versions of configuration files
 */
class GitConfigManager(val git: Git) extends ConfigManager with Logging {

  override def create(path: File, configData: ConfigData, comment: String): ConfigId = {
    logger.debug(s"create $path")
    val file = fileForPath(path)
    if (file.exists()) {
      throw new IOException("File already exists in repository: " + path)
    }
    put(path, configData, comment)
  }

  override def update(path: File, configData: ConfigData, comment: String): ConfigId = {
    logger.debug(s"update $path")
    val file = fileForPath(path)
    if (!file.exists()) throw new FileNotFoundException("File not found: " + path)
    put(path, configData, comment)
  }

  override def exists(path: File): Boolean = {
    logger.debug(s"exists $path")
    fileForPath(path).exists
  }

  override def delete(path: File, comment: String = "deleted"): Unit = {
    logger.debug(s"delete $path")
    if (!fileForPath(path).exists) {
      throw new FileNotFoundException("Can't delete " + path + " because it does not exist")
    }
    git.rm.addFilepattern(path.getPath).call()
    git.commit().setMessage(comment).call
    git.push.call()
  }

  override def get(path: File, id: Option[ConfigId]): Option[ConfigData] = {
    logger.debug(s"get $path")
    if (!fileForPath(path).exists) {
      None
    } else {
      if (!id.isEmpty) {
        // return the file for the given id
        val objId = ObjectId.fromString(id.get.asInstanceOf[GitConfigId].id)
        Some(new ConfigBytes(git.getRepository.open(objId).getBytes))
      } else {
        // return the latest version of the file (without checking Git)
        Some(new ConfigFile(fileForPath(path)))
      }
    }
  }

  def list(): List[ConfigFileInfo] = {
    logger.debug(s"list")
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

    list(treeWalk, List())
  }

  // Returns a list containing all known configuration files by walking the Git tree recursively and
  // collecting the resulting file info.
  @tailrec
  private def list(treeWalk: TreeWalk, result: List[ConfigFileInfo]) : List[ConfigFileInfo] = {
    if (treeWalk.next()) {
      val path = new File(treeWalk.getPathString)
      val objectId = treeWalk.getObjectId(0).name
      // TODO: Include create comment (history(path)(0).comment) or latest comment (history(path).last.comment)?
      val info = new ConfigFileInfo(path, GitConfigId(objectId), history(path).last.comment)
      list(treeWalk, info :: result)
    } else {
      result
    }
  }

  def history(path: File): List[ConfigFileHistory] = {
    logger.debug(s"history $path")
    val logCommand = git.log
      .add(git.getRepository.resolve(Constants.HEAD))
      .addPath(path.getPath)
    history(path, logCommand.call.iterator(), List())
  }

  // Returns a list of all known versions of a given path by recursively walking the Git history tree
  @tailrec
  private def history(path: File, it: java.util.Iterator[RevCommit], result: List[ConfigFileHistory]): List[ConfigFileHistory] = {
    if (it.hasNext) {
      val revCommit = it.next()
      val tree = revCommit.getTree
      val treeWalk = TreeWalk.forPath(git.getRepository, path.getPath, tree)
      if (treeWalk == null) {
        history(path, it, result)
      } else {
        val objectId = treeWalk.getObjectId(0)
        // TODO: Should comments be allowed to contain newlines? Might want to use longMessage?
        val comment = revCommit.getShortMessage
        val time = new Date(revCommit.getCommitTime * 1000L)
        val info = new ConfigFileHistory(GitConfigId(objectId.name), comment, time)
        history(path, it, info :: result)
      }
    } else {
      result
    }
  }

  /**
   * Creates or updates a config file with the given path and data and optional comment.
   *
   * @param path the config file path
   * @param configData the contents of the file
   * @param comment an optional comment to associate with this file
   * @return a unique id that can be used to refer to the file
   */
  private def put(path: File, configData: ConfigData, comment: String = ""): ConfigId = {
    val file = fileForPath(path)
    writeToFile(file, configData)
    val dirCache = git.add.addFilepattern(path.getPath).call()
    //    git.commit().setCommitter(name, email) // XXX using defaults from ~/.gitconfig for now
    git.commit().setMessage(comment).call
    git.push.call()
    GitConfigId(dirCache.getEntry(path.getPath).getObjectId.getName)
  }

  private def fileForPath(path: File): File = {
    new File(git.getRepository.getWorkTree, path.getPath)
  }

  private def writeToFile(file: File, configData: ConfigData): Unit = {
    Resource.fromFile(file).truncate(0L); // according to docs, this should happen below, but does not!
    Resource.fromFile(file).write(configData.getBytes)
  }

  //  // Sets the master repository (needed for git push/pull commands)
  //  private def trackMaster(git: Git): Unit = {
  //    git.branchCreate()
  //      .setName("master")
  //      .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
  //      .setStartPoint("origin/master")
  //      .setForce(true)
  //      .call
  //  }
}
