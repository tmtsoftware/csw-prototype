package csw.services.cs.core.git

import java.io.{ File, FileNotFoundException, IOException }
import java.net.URI
import java.nio.file.Files
import java.util.Date

import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexClient
import csw.services.cs.core.{ GitConfigId, _ }
import net.codejava.security.HashGeneratorUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk.{ RevCommit, RevWalk }
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

import scala.annotation.tailrec
import scala.concurrent.{Await, Future, ExecutionContextExecutor}
import scala.concurrent.duration._

/**
 * Used to initialize an instance of GitConfigManager with a given repository directory
 */
object GitConfigManager {

  private val tmpDir = System.getProperty("java.io.tmpdir")

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
  def apply(gitWorkDir: File, remoteRepo: URI)(implicit dispatcher: ExecutionContextExecutor): GitConfigManager = {
    // Init local repo
    val gitDir = new File(gitWorkDir, ".git")
    if (gitDir.exists()) {
      val git = new Git(new FileRepositoryBuilder().setGitDir(gitDir).build())
      val result = git.pull.call
      if (!result.isSuccessful) throw new IOException(result.toString)
      new GitConfigManager(git)
    } else {
      gitWorkDir.mkdirs()
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(remoteRepo.toString).call
      new GitConfigManager(git)
    }
  }

  /**
   * FOR TESTING: Deletes the contents of the given directory (recursively).
   * This is meant for use by tests that need to always start with an empty Git repository.
   */
  def deleteDirectoryRecursively(dir: File): Unit = {
    // just to be safe, don't delete anything that is not in /tmp/
    val p = dir.getPath
    if (!p.startsWith("/tmp/") && !p.startsWith(tmpDir))
      throw new RuntimeException(s"Refusing to delete $dir since not in /tmp/ or $tmpDir")

    if (dir.isDirectory) {
      dir.list.foreach {
        filePath ⇒
          val file = new File(dir, filePath)
          if (file.isDirectory) {
            deleteDirectoryRecursively(file)
          } else {
            file.delete()
          }
      }
      dir.delete()
    }
  }

  /**
   * Initializes a bare (main) repository in the given dir.
   * (A bare repository is one containing only the .git dir and no checked out files.)
   * This is used to create the main git repository that all the local repos point to.
   *
   * Note: We need to add at least one file after creating the main repository to avoid errors
   * later when pulling from an empty repo. Somehow adding the file also initializes
   * the master branch. The problem shows up when multiple clients create an instance
   * of GitConfigManager and try to pull from an empty, newly created git repo.
   *
   * @param dir directory to contain the new bare repository
   */
  def initBareRepo(dir: File)(implicit dispatcher: ExecutionContextExecutor): Unit = {
    // Create the new main repo
    Git.init.setDirectory(dir).setBare(true).call

    // Add a README file to a temporary clone of the main repo and push it to the new repo.
    val tmpDir = Files.createTempDirectory("TempConfigServiceRepo").toFile
    val gm = GitConfigManager(tmpDir, dir.toURI)
    try {
      Await.result(
        gm.create(new File("README"), ConfigString("This is the main Config Service Git repository.")),
        5.seconds)
    } finally {
      deleteDirectoryRecursively(tmpDir)
    }
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

/**
 * Uses JGit to manage versions of configuration files
 */
class GitConfigManager(val git: Git)(implicit dispatcher: ExecutionContextExecutor)
    extends ConfigManager with LazyLogging {

  // used to access the http server that manages oversize files
  val annex = ConfigServiceAnnexClient

  override def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] = {
    logger.debug(s"create $path")
    if (oversize) {
      createOversize(path, configData, comment)
    } else {
      Future {
        val file = fileForPath(path)
        if (file.exists()) {
          throw new IOException("File already exists in repository: " + path)
        }
        put(path, configData, comment)
      }
    }
  }

  override def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {
    logger.debug(s"update $path")
    Future(pull()).flatMap { _ ⇒
      val file = fileForPath(path)
      if (isOversize(file)) {
        updateOversize(path, configData, comment)
      } else {
        Future {
          if (!file.exists()) throw new FileNotFoundException("File not found: " + path)
          put(path, configData, comment)
        }
      }
    }
  }

  override def exists(path: File): Future[Boolean] = Future {
    logger.debug(s"exists $path")
    pull()
    val file = fileForPath(path)
    logger.debug(s"exists $path: $file exists? ${file.exists} || oversize? ${isOversize(file)}")
    file.exists || isOversize(file)
  }

  override def delete(path: File, comment: String = "deleted"): Future[Unit] = Future {
    deleteFile(path, comment)
  }

  private def deleteFile(path: File, comment: String = "deleted"): Unit = {
    logger.debug(s"delete $path")
    val file = fileForPath(path)
    pull()
    if (isOversize(file)) {
      deleteFile(shaFile(path), comment)
      file.delete()
    } else {
      if (!file.exists) {
        throw new FileNotFoundException("Can't delete " + path + " because it does not exist")
      }
      git.rm.addFilepattern(path.getPath).call()
      git.commit().setMessage(comment).call
      git.push.call()
      file.delete()
    }
  }


  override def get(path: File, id: Option[ConfigId]): Future[Option[ConfigData]] = {
    logger.debug(s"get $path")
    Future(pull()).flatMap { _ ⇒
      val file = fileForPath(path)
      if (isOversize(file)) {
        getOversize(path, id)
      } else {
        Future(getConfigData(path, id))
      }
    }
  }

  override def list(): Future[List[ConfigFileInfo]] = Future {
    logger.debug(s"list")
    pull()
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

  // Returns the contents of the given version of the file, if found
  private def getConfigData(path: File, id: Option[ConfigId]): Option[ConfigData] = {
    val file = fileForPath(path)
    if (!file.exists) {
      // assumes git pull was done, so file should be in working dir
      None
    } else {
      if (id.isDefined) {
        // return the file for the given id
        val objId = ObjectId.fromString(id.get.asInstanceOf[GitConfigId].id)
        Some(new ConfigBytes(git.getRepository.open(objId).getBytes))
      } else {
        // return the latest version of the file (without checking Git) (XXX needed?)
        Some(new ConfigFile(file))
      }
    }
  }

  // Returns a list containing all known configuration files by walking the Git tree recursively and
  // collecting the resulting file info.
  @tailrec
  private def list(treeWalk: TreeWalk, result: List[ConfigFileInfo]): List[ConfigFileInfo] = {
    if (treeWalk.next()) {
      val pathStr = treeWalk.getPathString
      val path = new File(pathStr)
      val origPath = origFile(path)
      val objectId = treeWalk.getObjectId(0).name
      // TODO: Include create comment (history(path)(0).comment) or latest comment (history(path).last.comment)?
      val info = new ConfigFileInfo(origPath, GitConfigId(objectId), hist(origPath).last.comment)
      list(treeWalk, info :: result)
    } else {
      result
    }
  }

  override def history(path: File): Future[List[ConfigFileHistory]] = Future(hist(path))

  private def hist(path: File): List[ConfigFileHistory] = {
    logger.debug(s"history $path")
    pull()
    // Check sha1 file history first (may have been deleted, so don't check if it exists)
    val shaPath = shaFile(path)
    val logCommand = git.log
      .add(git.getRepository.resolve(Constants.HEAD))
      .addPath(shaPath.getPath)
    val result = hist(shaPath, logCommand.call.iterator(), List())
    if (result.nonEmpty) {
      result
    } else {
      val logCommand = git.log
        .add(git.getRepository.resolve(Constants.HEAD))
        .addPath(path.getPath)
      hist(path, logCommand.call.iterator(), List())
    }
  }

  // Returns a list of all known versions of a given path by recursively walking the Git history tree
  @tailrec
  private def hist(path: File, it: java.util.Iterator[RevCommit], result: List[ConfigFileHistory]): List[ConfigFileHistory] = {
    if (it.hasNext) {
      val revCommit = it.next()
      val tree = revCommit.getTree
      val treeWalk = TreeWalk.forPath(git.getRepository, path.getPath, tree)
      if (treeWalk == null) {
        hist(path, it, result)
      } else {
        val objectId = treeWalk.getObjectId(0)
        // TODO: Should comments be allowed to contain newlines? Might want to use longMessage?
        val comment = revCommit.getShortMessage
        val time = new Date(revCommit.getCommitTime * 1000L)
        val info = new ConfigFileHistory(GitConfigId(objectId.name), comment, time)
        hist(path, it, info :: result)
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
    git.commit().setOnly(path.getPath).setMessage(comment).call
    git.push.call()
    GitConfigId(dirCache.getEntry(path.getPath).getObjectId.getName)
  }

  /**
   * Does a "git pull" to update the local repo with any changes made from the outside
   */
  private def pull(): Unit = {
    val result = git.pull.call
    if (!result.isSuccessful) throw new IOException(result.toString)
  }

  // Returns the absolute path of the file in the Git repository working tree
  private def fileForPath(path: File): File = new File(git.getRepository.getWorkTree, path.getPath)

  // Writes the given data to the given file (Note: The Files class is new in java1.7)
  private def writeToFile(file: File, configData: ConfigData): Unit = {
    val path = file.toPath
    if (!Files.isDirectory(path.getParent))
      Files.createDirectories(path.getParent)
    Files.write(path, configData.getBytes)
  }

  // File used to store the SHA-1 of the actual file, if oversized.
  private def shaFile(file: File): File =
    new File(s"${file.getPath}.sha1")

  // Inverse of shaFile
  private def origFile(file: File): File =
    if (file.getPath.endsWith(".sha1")) new File(file.getPath.dropRight(5)) else file

  // True if the .sha1 file exists, meaning the file needs special oversize handling.
  // Note: We only check if it exists in the working directory, not the repository.
  // Since the constructor does a git pull already, we assume all files that were not deleted are in the working dir.
  private def isOversize(file: File): Boolean = shaFile(file).exists

  /**
   * For large/binary files:
   * Creates the given file in the Git repo (actually creates a file named $path.sha1 containing the SHA-1
   * of the actual file).
   * The file data is stored on the config service annex server, where the file name is the SHA-1
   * of the data (making it unique for that data).
   *
   * @param path the relative path of the file in the git repo
   * @param configData contains the file's data
   * @param comment the create comment
   * @return the future ConfigId for the created $path.sha1, which only contains the SHA-1 of the actual file.
   */
  private def createOversize(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {
    val file = fileForPath(path)
    Future {
      val sha1File = shaFile(file)
      if (file.exists() || sha1File.exists())
        throw new IOException("File already exists in repository: " + file)
      writeToFile(file, configData)
    }.flatMap { _ ⇒
      annex.post(file).flatMap { sha1 ⇒
        create(shaFile(path), ConfigString(sha1), oversize = false, comment)
      }
    }
  }

  /**
   * For large/binary files:
   * Updates the given file in the Git repo (actually updates a file named $path.sha1 containing the SHA-1
   * of the actual file).
   * The file data is stored on the config service annex server, where the file name is the SHA-1
   * of the data (making it unique for that data).
   *
   * @param path the relative path of the file in the git repo
   * @param configData contains the file's data
   * @param comment the update comment
   * @return the future ConfigId for the updated $path.sha1, which only contains the SHA-1 of the actual file.
   */
  private def updateOversize(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {
    val file = fileForPath(path)
    Future {
      val sha1File = shaFile(file)
      writeToFile(file, configData)
    }.flatMap { _ ⇒
      annex.post(file).flatMap { sha1 ⇒
        update(shaFile(path), ConfigString(sha1), comment)
      }
    }
  }

  /**
   * For large/binary files:
   * Gets the given file from the Git repo (actually gets a file named $path.sha1 containing the SHA-1
   * of the actual file and uses that to get the actual file from the annex http server).
   *
   * @param path the file path
   * @param id an optional id used to specify a specific version to fetch
   *           (by default the latest version is returned)
   * @return a future object containing the configuration data, if found
   */
  private def getOversize(path: File, id: Option[ConfigId]): Future[Option[ConfigData]] = {
    get(shaFile(path), id).flatMap {
      case None ⇒ Future(None)
      case Some(configData) ⇒
        val sha1 = configData.toString
        val file = fileForPath(path)
        if (!file.exists() || (sha1 != HashGeneratorUtils.generateSHA1(file))) {
          annex.get(sha1, file).map {
            f ⇒ Some(ConfigFile(f))
          }
        } else {
          Future(Some(ConfigFile(file)))
        }
    }
  }
}
