package csw.services.cs.akka

import akka.actor._
import csw.services.cs.akka.ConfigServiceActor.RegisterWithLocationService
import csw.util.akka.Terminator

/**
 * Config Service standalone application.
 */
object ConfigService {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("ConfigService")
    val configManager = ConfigServiceActor.defaultConfigManager
    val configServiceActor = system.actorOf(ConfigServiceActor.props(configManager), "ConfigServiceActor")
    configServiceActor ! RegisterWithLocationService
    system.actorOf(Props(classOf[Terminator], configServiceActor), "terminator")
  }
}
