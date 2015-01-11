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
 * args: optional config service configuration file (see resource/reference.conf)
 */
object ConfigService extends App {
  val logger = Logger(LoggerFactory.getLogger("ConfigService"))
  implicit val system = ActorSystem("ConfigService")
  val settings = if (args.length != 0 && new File(args(0)).exists())
    ConfigServiceSettings(ConfigFactory.parseFile(new File(args(0))))
  else
    ConfigServiceSettings(system)

  logger.info(s"Config Service(${settings.name}}): using local repo: ${settings.gitLocalRepository}, remote repo: ${settings.gitMainRepository}")
  val configManager = GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.name)
  val configServiceActor = system.actorOf(ConfigServiceActor.props(configManager), "ConfigServiceActor")
  configServiceActor ! RegisterWithLocationService
  system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")

  // Start an HTTP server with the REST interface to the config service
  if (settings.startHttpServer)
    ConfigServiceHttpServer(configServiceActor, settings, registerWithLoc = true)
}
