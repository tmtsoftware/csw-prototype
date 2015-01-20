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
 *
 * args: An optional config service configuration file may be given on the command line
 * (see resource/reference.conf).
 *
 * If the -init option is given, the main git repository is initialized, if it does not yet exist
 * (in this case, the value of csw.services.cs.main-repository must be a file URI).
 *
 * For testing:
 * If the -delete option is given (in addition to -init) the repositories are first deleted
 * (but only if they are under /tmp - to avoid deleting an important one).
 */
object ConfigService extends App {
  val logger = Logger(LoggerFactory.getLogger("ConfigService"))
  implicit val system = ActorSystem("ConfigService")
  val delete = args.contains("-delete")
  val init = args.contains("-init")
  val otherArgs = args.filter(!_.startsWith("-"))

  val settings = if (otherArgs.length != 0 && new File(otherArgs(0)).exists())
    ConfigServiceSettings(ConfigFactory.parseFile(new File(otherArgs(0))))
  else
    ConfigServiceSettings(system)

  logger.info(s"Config Service(${settings.name}}): using local repo: ${settings.gitLocalRepository}, remote repo: ${settings.gitMainRepository}")

  if (init) {
    if (settings.gitMainRepository.getScheme != "file") {
      logger.error(s"Please specify a file URI for csw.services.cs.main-repository for testing")
      System.exit(1)
    }
    val gitMainRepo = new File(settings.gitMainRepository.getPath)

    if (delete) {
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
  configServiceActor ! RegisterWithLocationService
  system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")

  // Start an HTTP server with the REST interface to the config service
  if (settings.startHttpServer)
    ConfigServiceHttpServer(configServiceActor, settings, registerWithLoc = true)
}
