package csw.services.cs.akka

import akka.actor._
import csw.services.cs.akka.ConfigServiceActor.RegisterWithLocationService
import csw.services.cs.core.git.GitConfigManager
import csw.util.akka.Terminator

/**
 * Config Service standalone application.
 */
object ConfigService extends App {
  implicit val system = ActorSystem("ConfigService")
  val settings = ConfigServiceSettings(system)
  val configManager = GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.name)
  val configServiceActor = system.actorOf(ConfigServiceActor.props(configManager), "ConfigServiceActor")
  configServiceActor ! RegisterWithLocationService
  system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")

  // Start an HTTP server with the REST interface to the config service
  ConfigServiceHttpServer(configServiceActor, settings, registerWithLoc = true)
}
