package csw.services.cs.core.svn

import java.io.{File, FileNotFoundException, IOException}
import java.net.URI
import java.nio.file.Files
import java.util.Date

import akka.actor.ActorRefFactory
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexClient
import csw.services.cs.core.{SvnConfigId, _}
import net.codejava.security.HashGeneratorUtils
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.io.{SVNRepository, SVNRepositoryFactory}

import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNWCUtil
import org.tmatesoft.svn.core.SVNException
import scala.collection.JavaConverters._


import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Used to initialize an instance of SvnConfigManager with a given repository directory
  */
object SvnConfigManager {

  private val tmpDir = System.getProperty("java.io.tmpdir")

  // $file.default holds the id of the default version of file
  private val defaultSuffix = ".default"

  // $file.sha1 holds the SHA-1 hash of oversize files that are stored on the config service annex http server
  private val sha1Suffix = ".sha1"

  /**
    * Creates and returns a SvnConfigManager instance using the given directory as the
    * local Svn repository root (directory containing .svn dir) and the given
    * URI as the remote, central Svn repository.
    * If the local repository already exists, it is opened, otherwise it is created.
    * An exception is thrown if the remote repository does not exist.
    *
    * @param svnWorkDir top level directory to use for storing configuration files and the local svn repository (under .svn)
    * @param remoteRepo the URI of the remote, main repository
    * @param name       the name of this service
    * @return a new SvnConfigManager configured to use the given local and remote repositories
    */
  def apply(svnWorkDir: File, remoteRepo: URI, name: String = "Config Service")
           (implicit context: ActorRefFactory): SvnConfigManager = {

    // Init local repo
    //    val svnDir = new File(svnWorkDir, ".svn")
    //    if (svnDir.exists()) {
    //      val svn = new Svn(new FileRepositoryBuilder().setSvnDir(svnDir).build())
    //      val result = svn.pull.call
    //      if (!result.isSuccessful) throw new IOException(result.toString)
    //      new SvnConfigManager(svn, name)
    //    } else {
    //      svnWorkDir.mkdirs()
    //      val svn = Svn.cloneRepository.setDirectory(svnWorkDir).setURI(remoteRepo.toString).call
    //      new SvnConfigManager(svn, name)
    //    }

    //Set up connection protocols support:
    //http:// and https://
    DAVRepositoryFactory.setup()
    //svn://, svn+xxx:// (svn+ssh:// in particular)
    SVNRepositoryFactoryImpl.setup()
    //file:///
    FSRepositoryFactory.setup()

    val svn = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(remoteRepo.toString))
    val authManager = SVNWCUtil.createDefaultAuthenticationManager()
    svn.setAuthenticationManager(authManager)
    new SvnConfigManager(svn, name)
  }

  /**
    * FOR TESTING: Deletes the contents of the given directory (recursively).
    * This is meant for use by tests that need to always start with an empty Svn repository.
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
    * Initializes an svn repository in the given dir.
    *
    * @param dir directory to contain the new repository
    */
  def initSvnRepo(dir: File)(implicit context: ActorRefFactory): Unit = {
    // Create the new main repo
    //    Svn.init.setDirectory(dir).setBare(true).call
    FSRepositoryFactory.setup()
    //    val svnUrl =
    SVNRepositoryFactory.createLocalRepository(dir, false, true)

    //    // Add a README file to a temporary clone of the main repo and push it to the new repo.
    //    val tmpDir = Files.createTempDirectory("TempConfigServiceRepo").toFile
    //    val gm = SvnConfigManager(tmpDir, dir.toURI)
    //    try {
    //      // XXX TODO: return Future[Unit] instead?
    //      Await.result(
    //        gm.create(new File("README"), ConfigData("This is the main Config Service Svn repository.")),
    //        10.seconds
    //      )
    //    } finally {
    //      deleteDirectoryRecursively(tmpDir)
    //    }
  }

}

/**
  * Uses JSvn to manage versions of configuration files.
  * Special handling is available for large/binary files (oversize option in create).
  * Oversize files are stored on an "annex" server using the SHA-1 hash of the file
  * contents for the name (similar to the way Svn stores file objects).
  *
  * Note that although the API is non-blocking, we need to be careful when dealing
  * with the file system (the local Svn repo), which is static, and not attempt multiple
  * conflicting file read, write or Svn operations at once. The remote (bare) Svn repo should
  * be able to handle the concurrent usage, but not the local repo, which has files in the
  * working directory. Having the files checked out in working directory should help avoid
  * having to download them every time an application starts.
  *
  * Only one instance of this class should exist for a given local Svn repository.
  *
  * @param svn  used to access the svn repository
  * @param name the name of the service
  */
class SvnConfigManager(val svn: SVNRepository, override val name: String)(implicit context: ActorRefFactory)
  extends ConfigManager with LazyLogging {

  import context.dispatcher

  // used to access the http server that manages oversize files
  val annex = ConfigServiceAnnexClient

  override def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] = {
    def createOversize(file: File): Future[ConfigId] = {
      val sha1File = shaFile(file)
      if (file.exists() || sha1File.exists()) {
        Future.failed(new IOException("File already exists in repository: " + file))
      } else for {
        _ ← configData.writeToFile(file)
        sha1 ← annex.post(file)
        configId ← create(shaFile(path), ConfigData(sha1), oversize = false, comment)
      } yield configId
    }

    logger.debug(s"create $path")
    val file = fileForPath(path)
    if (oversize) {
      createOversize(file)
    } else {
      if (file.exists()) {
        Future.failed(new IOException("File already exists in repository: " + path))
      } else {
        put(path, configData, comment)
      }
    }

  }

  override def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {
    def updateOversize(file: File): Future[ConfigId] = {
      for {
        _ ← configData.writeToFile(file)
        sha1 ← annex.post(file)
        configId ← update(shaFile(path), ConfigData(sha1), comment)
      } yield configId
    }

    logger.debug(s"update $path")
    val file = fileForPath(path)
    Future(pull()).flatMap { _ ⇒
      if (isOversize(file)) {
        updateOversize(file)
      } else {
        if (!file.exists()) {
          Future.failed(new FileNotFoundException("File not found: " + path))
        } else {
          put(path, configData, comment)
        }
      }
    }
  }

  override def createOrUpdate(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] =
    for {
      exists ← exists(path)
      result ← if (exists) update(path, configData, comment) else create(path, configData, oversize, comment)
    } yield result

  override def exists(path: File): Future[Boolean] = Future {
    logger.debug(s"exists $path")
    pull()
    val file = fileForPath(path)
    logger.debug(s"exists $path: $file exists? ${file.exists} || oversize? ${isOversize(file)}")
    file.exists || isOversize(file)
  }

  override def delete(path: File, comment: String = "deleted"): Future[Unit] = {
    def deleteFile(path: File, comment: String = "deleted"): Unit = {
      logger.debug(s"delete $path")
      val file = fileForPath(path)
      pull()
      if (isOversize(file)) {
        deleteFile(shaFile(path), comment)
        if (file.exists()) file.delete()
      } else {
        if (!file.exists) {
          throw new FileNotFoundException("Can't delete " + path + " because it does not exist")
        }
        svn.rm.addFilepattern(path.getPath).call()
        svn.commit().setMessage(comment).call
        svn.push.call()
        if (file.exists()) file.delete()
      }
    }

    Future {
      deleteFile(path, comment)
    }
  }

  override def get(path: File, id: Option[ConfigId]): Future[Option[ConfigData]] = {

    // Get oversize files that are stored in the annex server
    def getOversize(file: File): Future[Option[ConfigData]] = {
      for {
        opt ← get(shaFile(path), id)
        data ← getData(file, opt)
      } yield data

    }
    // Gets the actual file data using the SHA-1 value contained in the checked in file
    def getData(file: File, opt: Option[ConfigData]): Future[Option[ConfigData]] = {
      opt match {
        case None ⇒ Future(None)
        case Some(configData) ⇒
          for {
            sha1 ← configData.toFutureString
            configDataOpt ← getIfNeeded(file, sha1)
          } yield configDataOpt
      }
    }

    // If the file matches the SHA-1 hash, return a future for it, otherwise get it from the annex server
    def getIfNeeded(file: File, sha1: String): Future[Option[ConfigData]] = {
      if (!file.exists() || (sha1 != HashGeneratorUtils.generateSHA1(file))) {
        annex.get(sha1, file).map {
          _ ⇒ Some(ConfigData(file))
        }
      } else {
        Future(Some(ConfigData(file)))
      }
    }

    // Returns the contents of the given version of the file, if found
    def getConfigData(file: File): Option[ConfigData] = {
      if (!file.exists) {
        // assumes svn pull was done, so file should be in working dir
        None
      } else {
        if (id.isDefined) {
          // return the file for the given id
          val objId = ObjectId.fromString(id.get.asInstanceOf[SvnConfigId].id)
          Some(ConfigData(svn.getRepository.open(objId).getBytes))
        } else {
          // return the latest version of the file from the working dir
          Some(ConfigData(file))
        }
      }
    }

    logger.debug(s"get $path")
    val file = fileForPath(path)

    Future(pull()).flatMap { _ ⇒
      if (isOversize(file)) {
        getOversize(file)
      } else {
        Future(getConfigData(file))
      }
    }
  }

  override def list(): Future[List[ConfigFileInfo]] = Future {

    // Returns a list containing all known configuration files by walking the Svn tree recursively and
    // collecting the resulting file info.
    @tailrec
    def list(treeWalk: TreeWalk, result: List[ConfigFileInfo]): List[ConfigFileInfo] = {
      if (treeWalk.next()) {
        val pathStr = treeWalk.getPathString
        if (pathStr.endsWith(SvnConfigManager.defaultSuffix)) {
          list(treeWalk, result)
        } else {
          val path = new File(pathStr)
          val origPath = origFile(path)
          val objectId = treeWalk.getObjectId(0).name
          val info = new ConfigFileInfo(origPath, SvnConfigId(objectId), hist(origPath, 1).head.comment)
          list(treeWalk, info :: result)
        }
      } else {
        result
      }
    }

    logger.debug(s"list")
    pull()
    val repo = svn.getRepository

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

    val result = list(treeWalk, List())
    walk.dispose()
    result
  }

  override def history(path: File, maxResults: Int = Int.MaxValue): Future[List[ConfigFileHistory]] =
    Future(hist(path, maxResults))

  private def hist(path: File, maxResults: Int = Int.MaxValue): List[ConfigFileHistory] = {
    logger.debug(s"history $path")
    pull()
    // Check sha1 file history first (may have been deleted, so don't check if it exists)
    val shaPath = shaFile(path)
    val logCommand = svn.log
      .add(svn.getRepository.resolve(Constants.HEAD))
      .addPath(shaPath.getPath)
    val result = hist(shaPath, logCommand.call.iterator(), List(), maxResults)
    if (result.nonEmpty) {
      result
    } else {
      val logCommand = svn.log
        .add(svn.getRepository.resolve(Constants.HEAD))
        .addPath(path.getPath)
      hist(path, logCommand.call.iterator(), List(), maxResults)
    }
  }

  // Returns a list of all known versions of a given path by recursively walking the Svn history tree
  @tailrec
  private def hist(
                    path: File,
                    it: java.util.Iterator[RevCommit],
                    result: List[ConfigFileHistory],
                    maxResults: Int
                  ): List[ConfigFileHistory] = {
    if (it.hasNext && result.size < maxResults) {
      val revCommit = it.next()
      val tree = revCommit.getTree
      val treeWalk = TreeWalk.forPath(svn.getRepository, path.getPath, tree)
      if (treeWalk == null) {
        hist(path, it, result, maxResults)
      } else {
        val objectId = treeWalk.getObjectId(0)
        // TODO: Should comments be allowed to contain newlines? Might want to use longMessage?
        val comment = revCommit.getShortMessage
        val time = new Date(revCommit.getCommitTime * 1000L)
        val info = new ConfigFileHistory(SvnConfigId(objectId.name), comment, time)
        hist(path, it, result :+ info, maxResults)
      }
    } else {
      result
    }
  }

  /**
    * Creates or updates a config file with the given path and data and optional comment.
    *
    * @param path       the config file path
    * @param configData the contents of the file
    * @param comment    an optional comment to associate with this file
    * @return a future unique id that can be used to refer to the file
    */
  private def put(path: File, configData: ConfigData, comment: String = ""): Future[ConfigId] = {
    val file = fileForPath(path)
    for {
      _ ← configData.writeToFile(file)
      configId ← Future {
        val dirCache = svn.add.addFilepattern(path.getPath).call()
        // svn.commit().setCommitter(name, email) // XXX using defaults from ~/.svnconfig for now
        svn.commit().setOnly(path.getPath).setMessage(comment).call
        svn.push.call()
        SvnConfigId(dirCache.getEntry(path.getPath).getObjectId.getName)
      }
    } yield configId
  }

  /**
    * Does a "svn pull" to update the local repo with any changes made from the outside
    */
  private def pull(): Unit = {
    val result = svn.pull.call
    if (!result.isSuccessful) throw new IOException(result.toString)
  }

  // Returns the absolute path of the file in the Svn repository working tree
  private def fileForPath(path: File): File = new File(svn.getRepository.getWorkTree, path.getPath)

  // File used to store the SHA-1 of the actual file, if oversized.
  private def shaFile(file: File): File =
    new File(s"${file.getPath}${SvnConfigManager.sha1Suffix}")

  // Inverse of shaFile
  private def origFile(file: File): File =
    if (file.getPath.endsWith(SvnConfigManager.sha1Suffix)) new File(file.getPath.dropRight(5)) else file

  // True if the .sha1 file exists, meaning the file needs special oversize handling.
  // Note: We only check if it exists in the working directory, not the repository.
  // Since the constructor does a svn pull already, we assume all files that were not deleted are in the working dir.
  private def isOversize(file: File): Boolean = shaFile(file).exists

  // --- Default version handling ---

  // Returns the current version of the file, if known
  private def getCurrentVersion(path: File): Option[ConfigId] = {
    hist(path, 1).headOption.map(_.id)
  }

  // File used to store the id of the default version of the file.
  private def defaultFile(file: File): File =
    new File(s"${file.getPath}${SvnConfigManager.defaultSuffix}")

  // True if the .default file exists, meaning the file has a default version set.
  // Note: We only check if it exists in the working directory, not the repository.
  // Since the constructor and most methods do a svn pull already,
  // we assume all files that were not deleted are in the working dir.
  private def hasDefault(file: File): Boolean = defaultFile(file).exists

  def setDefault(path: File, id: Option[ConfigId] = None): Future[Unit] = {
    logger.debug(s"setDefault $path $id")
    (if (id.isDefined) id else getCurrentVersion(path)) match {
      case Some(configId) ⇒
        create(defaultFile(path), ConfigData(configId.id)).map(_ ⇒ ())
      case None ⇒
        Future.failed(new RuntimeException(s"Unknown path $path"))
    }
  }

  def resetDefault(path: File): Future[Unit] = {
    logger.debug(s"resetDefault $path")
    delete(defaultFile(path))
  }

  def getDefault(path: File): Future[Option[ConfigData]] = {
    logger.debug(s"getDefault $path")
    val currentId = getCurrentVersion(path)
    if (currentId.isEmpty)
      Future(None)
    else for {
      d ← get(defaultFile(path))
      id ← if (d.isDefined) d.get.toFutureString else Future(currentId.get.id)
      result ← get(path, Some(ConfigId(id)))
    } yield result
  }
}
