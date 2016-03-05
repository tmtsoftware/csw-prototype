package csw.services.cs.core.svn

import java.io.{IOException, ByteArrayOutputStream, File, FileNotFoundException}
import java.net.URI

import akka.actor.ActorRefFactory
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.apps.configServiceAnnex.ConfigServiceAnnexClient
import csw.services.cs.CommitBuilder
import csw.services.cs.core._

import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl
import org.tmatesoft.svn.core.wc2._
import org.tmatesoft.svn.core._
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.{SVNRevision, SVNClientManager, SVNWCUtil}

import scala.concurrent.Future

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

    //Set up connection protocols support:
    //http:// and https://
    DAVRepositoryFactory.setup()
    //svn://, svn+xxx:// (svn+ssh:// in particular)
    SVNRepositoryFactoryImpl.setup()
    //file:///
    FSRepositoryFactory.setup()

    val url = SVNURL.parseURIEncoded(remoteRepo.toString)
    val svn = SVNRepositoryFactory.create(url)
    val authManager = SVNWCUtil.createDefaultAuthenticationManager()
    svn.setAuthenticationManager(authManager)
    new SvnConfigManager(svn, svnWorkDir, name)
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
    FSRepositoryFactory.setup()
    SVNRepositoryFactory.createLocalRepository(dir, false, true)
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
  * @param svn        used to access the svn repository
  * @param svnWorkDir the working directory in which the files from the repository are checked out
  * @param name       the name of the service
  */
class SvnConfigManager(val svn: SVNRepository, svnWorkDir: File, override val name: String)(implicit context: ActorRefFactory)
  extends ConfigManager with LazyLogging {

  import context.dispatcher

  // used to access the http server that manages oversize files
  val annex = ConfigServiceAnnexClient

  val url = svn.getRepositoryRoot(true)

  override def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] = {
    def createOversize(file: File): Future[ConfigId] = {
      for {
        _ ← configData.writeToFile(file)
        sha1 ← annex.post(file)
        configId ← create(shaFile(path), ConfigData(sha1), oversize = false, comment)
      } yield configId
    }

    // If the file does not already exists in the repo, create it
    def createImpl(present: Boolean): Future[ConfigId] = {
      if (present) {
        Future.failed(new IOException("File already exists in repository: " + path))
      } else if (oversize) {
        createOversize(fileForPath(path))
      } else {
        put(path, configData, update = false, comment)
      }
    }

    logger.debug(s"create $path")
    for {
      present <- exists(path)
      configId <- createImpl(present)
    } yield configId
  }

  override def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] = {

    def updateOversize(file: File): Future[ConfigId] = {
      // XXX TODO FIXME: use temp file and delete afterwards
      for {
        _ ← configData.writeToFile(file)
        sha1 ← annex.post(file)
        configId ← update(shaFile(path), ConfigData(sha1), comment)
      } yield configId
    }

    // If the file already exists in the repo, update it
    def updateImpl(present: Boolean): Future[ConfigId] = {
      if (!present) {
        Future.failed(new FileNotFoundException("File not found: " + path))
      } else if (isOversize(path)) {
        updateOversize(fileForPath(path))
      } else {
        put(path, configData, update = true, comment)
      }
    }

    logger.debug(s"update $path")
    for {
      present <- exists(path)
      configId <- updateImpl(present)
    } yield configId
  }

  override def createOrUpdate(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] =
    for {
      exists ← exists(path)
      result ← if (exists) update(path, configData, comment) else create(path, configData, oversize, comment)
    } yield result

  override def exists(path: File): Future[Boolean] = Future(pathExists(path))

  private def pathExists(path: File): Boolean = {
    logger.debug(s"exists $path")
    svn.checkPath(path.getPath, -1L) == SVNNodeKind.FILE || isOversize(path)
  }


  override def delete(path: File, comment: String = "deleted"): Future[Unit] = {
    def deleteFile(path: File, comment: String = "deleted"): Unit = {
      logger.debug(s"delete $path")
      if (isOversize(path)) {
        deleteFile(shaFile(path), comment)
      } else {
        if (!pathExists(path)) {
          throw new FileNotFoundException("Can't delete " + path + " because it does not exist")
        }

        val svnOperationFactory = new SvnOperationFactory()
        try {
          val remoteDelete = svnOperationFactory.createRemoteDelete()
          remoteDelete.setSingleTarget(SvnTarget.fromURL(url.appendPath(path.getPath, false)))
          remoteDelete.setCommitMessage(comment)
          remoteDelete.run()
        } finally {
          svnOperationFactory.dispose()
        }
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
            configDataOpt ← getFromAnnexServer(file, sha1)
          } yield configDataOpt
      }
    }

    // If the file matches the SHA-1 hash, return a future for it, otherwise get it from the annex server
    def getFromAnnexServer(file: File, sha1: String): Future[Option[ConfigData]] = {
      annex.get(sha1, file).map {
        _ ⇒ Some(ConfigData(file))
      }
    }

    // Returns the contents of the given version of the file, if found
    def getConfigData: Future[Option[ConfigData]] = Future {
      val os = new ByteArrayOutputStream()
      svn.getFile(path.getPath, svnRevision(id).getNumber, null, os)
      Some(ConfigData(os.toByteArray))
    }

    // If the file exists in the repo, get its data
    def getImpl(present: Boolean): Future[Option[ConfigData]] = {
      if (!present) {
        Future(None)
      } else if (isOversize(path)) {
        getOversize(fileForPath(path))
      } else {
        getConfigData
      }
    }

    // -- svn get --
    logger.debug(s"get $path")
    for {
      present <- exists(path)
      configData <- getImpl(present)
    } yield configData
  }

  override def list(): Future[List[ConfigFileInfo]] = Future {
    var entries = List[SVNDirEntry]()
    val svnOperationFactory = new SvnOperationFactory()
    try {
      val svnList = svnOperationFactory.createList()
      svnList.setSingleTarget(SvnTarget.fromURL(url, SVNRevision.HEAD))
      svnList.setRevision(SVNRevision.HEAD)
      svnList.setDepth(SVNDepth.INFINITY)
      svnList.setReceiver(new ISvnObjectReceiver[SVNDirEntry] {
        override def receive(target: SvnTarget, e: SVNDirEntry): Unit = {
          entries = e :: entries
        }
      })
      svnList.run()
    } finally {
      svnOperationFactory.dispose()
    }
    entries.filter(_.getKind == SVNNodeKind.FILE).sortWith(_.getRelativePath < _.getRelativePath)
      .map(e => ConfigFileInfo(new File(e.getRelativePath), ConfigId(e.getRevision), e.getCommitMessage))
  }

  override def history(path: File, maxResults: Int = Int.MaxValue): Future[List[ConfigFileHistory]] =
    Future(hist(path, maxResults))

  private def hist(path: File, maxResults: Int = Int.MaxValue): List[ConfigFileHistory] = {
    val clientManager = SVNClientManager.newInstance()
    var logEntries = List[SVNLogEntry]()
    try {
      val logClient = clientManager.getLogClient
      logClient.doLog(url, Array(path.getPath), SVNRevision.HEAD, null, null, true, true, maxResults,
        new ISVNLogEntryHandler() {
          override def handleLogEntry(logEntry: SVNLogEntry): Unit = logEntries = logEntry :: logEntries
        })
    } finally {
      clientManager.dispose()
    }
    logEntries.sortWith(_.getRevision > _.getRevision)
      .map(e => ConfigFileHistory(ConfigId(e.getRevision), e.getMessage, e.getDate))
  }

  /**
    * Creates or updates a config file with the given path and data and optional comment.
    *
    * @param path       the config file path
    * @param configData the contents of the file
    * @param comment    an optional comment to associate with this file
    * @return a future unique id that can be used to refer to the file
    */
  private def put(path: File, configData: ConfigData, update: Boolean, comment: String = ""): Future[ConfigId] = {
    val os = new ByteArrayOutputStream()
    for {
      _ <- configData.writeToOutputStream(os)
    } yield {
      val svnOperationFactory = new SvnOperationFactory
      try {
        val commitBuilder = new CommitBuilder(url)
        commitBuilder.setCommitMessage(comment)
        val data = os.toByteArray
        if (update)
          commitBuilder.changeFile(path.getPath, data)
        else
          commitBuilder.addFile(path.getPath, data)
        val commitInfo = commitBuilder.commit()
        ConfigId(commitInfo.getNewRevision)
      } finally {
        svnOperationFactory.dispose()
      }
    }
  }

  // Gets the svn revision from the given id, defaulting to HEAD
  private def svnRevision(id: Option[ConfigId] = None): SVNRevision = {
    id match {
      case Some(configId) => SVNRevision.create(configId.id.toLong)
      case None => SVNRevision.HEAD
    }
  }

  // Returns the absolute path of the file in the Svn repository working tree
  //  private def fileForPath(path: File): File = new File(svn.getFullPath(path.getPath))
  private def fileForPath(path: File): File = new File(svnWorkDir, path.getPath)


  // File used to store the SHA-1 of the actual file, if oversized.
  private def shaFile(file: File): File =
    new File(s"${file.getPath}${SvnConfigManager.sha1Suffix}")

  // True if the .sha1 file exists, meaning the file needs special oversize handling.
  private def isOversize(path: File): Boolean = svn.checkPath(shaFile(path).getPath, -1L) == SVNNodeKind.FILE

  // --- Default version handling ---

  // Returns the current version of the file, if known
  private def getCurrentVersion(path: File): Option[ConfigId] = {
    hist(path, 1).headOption.map(_.id)
  }

  // File used to store the id of the default version of the file.
  private def defaultFile(file: File): File =
    new File(s"${file.getPath}${SvnConfigManager.defaultSuffix}")

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
