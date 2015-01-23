package csw.services.cs.akka

import java.io.File

import akka.actor._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.Logger
import csw.services.cs.akka.ConfigServiceActor.RegisterWithLocationService
import csw.services.cs.core.git.GitConfigManager
import csw.util.akka.Terminator
import org.slf4j.LoggerFactory

/**
 * Config Service standalone application.
 */
object ConfigService extends App {
  val logger = Logger(LoggerFactory.getLogger("ConfigService"))

  /**
   * Command line options: [--config <config> --init --delete --http]
   *
   * @param config optional config file to use for config service settings, if needed
   * @param init the main git repository is initialized, if it does not yet exist
   * (in this case, the value of csw.services.cs.main-repository must be a file URI).
   * @param delete (implies --init) the repositories are first deleted
   * (but only if they are under /tmp - to avoid accidentally deleting any important data)
   * @param nohttp don't start the http server (otherwise it is started, if configured in config file)
   * @param noregister don't register with the location service (default is to register)
   */
  case class Options(config: Option[File] = None, init: Boolean = false, delete: Boolean = false,
                     nohttp: Boolean = false, noregister: Boolean = false)

  val parser = new scopt.OptionParser[Options]("scopt") {
    head("cs", System.getProperty("CSW_VERSION"))

    opt[File]("config") action { (x, c) ⇒
      c.copy(config = Some(x))
    } text "optional config file to use for config service settings"

    opt[Unit]("init") action { (_, c) ⇒
      c.copy(init = true)
    } text "the main git repository is initialized, if it does not yet exist"

    opt[Unit]("delete") action { (_, c) ⇒
      c.copy(delete = true, init = true)
    } text "(implies --init) existing repositories are first deleted"

    opt[Unit]("nohttp") action { (_, c) ⇒
      c.copy(nohttp = true)
    } text "don't start the http server"

    opt[Unit]("noregister") action { (_, c) ⇒
      c.copy(noregister = true)
    } text "don't register with the location service"
  }

  parser.parse(args, Options()) match {
    case Some(options) ⇒ run(options)
    case None          ⇒ System.exit(1)
  }

  def run(options: Options): Unit = {
    implicit val system = ActorSystem("ConfigService")

    val settings = options.config match {
      case Some(file) ⇒ ConfigServiceSettings(ConfigFactory.parseFile(file))
      case None       ⇒ ConfigServiceSettings(system)
    }

    logger.info(s"Config Service(${settings.name}}): using local repo: ${settings.gitLocalRepository}, remote repo: ${settings.gitMainRepository}")

    if (options.init) {
      if (settings.gitMainRepository.getScheme != "file") {
        logger.error(s"Please specify a file URI for csw.services.cs.main-repository for testing")
        System.exit(1)
      }
      val gitMainRepo = new File(settings.gitMainRepository.getPath)

      if (options.delete) {
        GitConfigManager.deleteDirectoryRecursively(gitMainRepo)
        val gitLocalRepo = new File(settings.gitLocalRepository.getPath)
        GitConfigManager.deleteDirectoryRecursively(gitLocalRepo)
      }

      if (!new File(gitMainRepo, ".git").exists) {
        logger.info(s"creating new main repo under $gitMainRepo")
        GitConfigManager.initBareRepo(gitMainRepo)
      }
    }

    val configManager = GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.name)
    val configServiceActor = system.actorOf(ConfigServiceActor.props(configManager), "ConfigServiceActor")

    if (!options.noregister)
      configServiceActor ! RegisterWithLocationService

    system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")

    // Start an HTTP server with the REST interface to the config service
    if (settings.startHttpServer && !options.nohttp)
      ConfigServiceHttpServer(configServiceActor, settings, registerWithLoc = true)
  }
}
