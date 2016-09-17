package csw.services.cs.akka

import java.io.File

import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.apps.configServiceAnnex.{ConfigServiceAnnexServer, ConfigServiceAnnexServerApp}
import csw.services.cs.core.git.GitConfigManager
import csw.services.cs.core.svn.SvnConfigManager
import csw.services.loc.LocationService
import org.slf4j.LoggerFactory

/**
 * Config Service standalone application.
 */
object ConfigService extends App {
  LocationService.initInterface()
  val logger = Logger(LoggerFactory.getLogger(ConfigService.getClass))

  // Command line options: See parser below for option descriptions
  case class Options(
    config:     Option[File] = None,
    init:       Boolean      = false,
    delete:     Boolean      = false,
    nohttp:     Boolean      = false,
    noAnnex:    Boolean      = false,
    noregister: Boolean      = false
  )

  val parser = new scopt.OptionParser[Options]("scopt") {
    head("cs", System.getProperty("CSW_VERSION"))

    opt[File]("config") action { (x, c) =>
      c.copy(config = Some(x))
    } text "optional config file to use for config service settings"

    opt[Unit]("init") action { (_, c) =>
      c.copy(init = true)
    } text "the repository is initialized, if it does not yet exist"

    opt[Unit]("delete") action { (_, c) =>
      c.copy(delete = true, init = true)
    } text "(implies --init) existing repositories are first deleted"

    opt[Unit]("nohttp") action { (_, c) =>
      c.copy(nohttp = true)
    } text "don't start the http server"

    opt[Unit]("noannex") action { (_, c) =>
      c.copy(noAnnex = true)
    } text "don't start the annex server (an http server used to manage large/oversized files)"

    opt[Unit]("noregister") action { (_, c) =>
      c.copy(noregister = true)
    } text "don't register with the location service"

    help("help")
    version("version")
  }

  parser.parse(args, Options()) match {
    case Some(options) => run(options)
    case None          => System.exit(1)
  }

  def run(options: Options): Unit = {
    implicit val system = ActorSystem("ConfigService")

    val settings = options.config match {
      case Some(file) => ConfigServiceSettings(ConfigFactory.parseFile(file))
      case None       => ConfigServiceSettings(system)
    }

    if (options.init) {
      if (settings.mainRepository.getScheme != "file") {
        logger.error(s"Please specify a file URI for csw.services.cs.main-repository for testing")
        System.exit(1)
      }
      val mainRepo = new File(settings.mainRepository.getPath)

      if (options.delete) {
        // Note: both blocks do the same thing...
        if (settings.useSvn) {
          logger.debug(s"Config Service(${settings.name}}): using svn repository: ${settings.mainRepository}")
          SvnConfigManager.deleteDirectoryRecursively(mainRepo)
        } else {
          logger.debug(s"Config Service(${settings.name}}): using local git repository: ${settings.localRepository}, remote repository: ${settings.mainRepository}")
          GitConfigManager.deleteDirectoryRecursively(mainRepo)
          val gitLocalRepo = new File(settings.localRepository.getPath)
          GitConfigManager.deleteDirectoryRecursively(gitLocalRepo)
        }
      }

      if (settings.useSvn) {
        if (!new File(mainRepo, ".svn").exists) {
          logger.debug(s"creating new svn repo under $mainRepo")
          SvnConfigManager.initSvnRepo(mainRepo)
        }
      } else {
        if (!new File(mainRepo, ".git").exists) {
          logger.debug(s"creating new git main repo under $mainRepo")
          GitConfigManager.initBareRepo(mainRepo)
        }
      }
    }

    val configManager = settings.getConfigManager
    val configServiceActor = system.actorOf(ConfigServiceActor.props(configManager, !options.noregister), "ConfigServiceActor")

    system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")

    // Start an HTTP server with the REST interface to the config service
    if (settings.startHttpServer && !options.nohttp)
      ConfigServiceHttpServer(configServiceActor, settings, registerWithLoc = true)

    // Start the annex http server that handles oversized files
    if (!options.noAnnex)
      ConfigServiceAnnexServer(registerWithLoc = true)
  }

  /**
   * Exits the application when the given actor stops
   *
   * @param ref reference to the main actor of an application
   */
  class Terminator(ref: ActorRef) extends Actor with ActorLogging {
    context watch ref

    def receive = {
      case Terminated(_) =>
        log.debug("{} has terminated, shutting down system", ref.path)
        context.system.terminate()
    }
  }

}
