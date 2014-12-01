package csw.services.cs.akka

import akka.actor._
import csw.services.cs.akka.ConfigServiceActor.RegisterWithLocationService
import csw.services.ls.LocationServiceActor.{ ServicesReady, ServiceType, ServiceId }
import csw.util.akka.Terminator

/**
 * Config Service standalone application.
 */
object ConfigService extends App {
  implicit val system = ActorSystem("ConfigService")
  val configManager = ConfigServiceActor.defaultConfigManager
  val configServiceActor = system.actorOf(ConfigServiceActor.props(configManager), "ConfigServiceActor")
  configServiceActor ! RegisterWithLocationService
  system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")
}
