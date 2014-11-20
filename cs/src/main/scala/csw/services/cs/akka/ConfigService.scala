package csw.services.cs.akka

import akka.actor._
import csw.util.akka.Terminator

/**
 * Config Service standalone application.
 */
object ConfigService {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ConfigService")
    val configManager = ConfigServiceActor.defaultConfigManager(system)
    val a = system.actorOf(ConfigServiceActor.props(configManager), "ConfigServiceActor")
    system.actorOf(Props(classOf[Terminator], a), "terminator")
  }
}
