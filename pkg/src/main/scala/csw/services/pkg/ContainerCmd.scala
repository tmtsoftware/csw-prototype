package csw.services.pkg

import java.io.File

import akka.actor._
import com.typesafe.config.{Config, ConfigFactory}
import csw.util.akka.Terminator

/**
 * A command line application for creating containers with components specified in a config file.
 */
object ContainerCmd extends App {
  if (args.length != 1) error("Expected a config file argument")
  val configFile = new File(args(0))
  if (!configFile.exists()) {
    error(s"File '$configFile' does not exist")
  }
  val config = ConfigFactory.parseFileAnySyntax(configFile)
  val system = ActorSystem("ContainerCmd")
  val a = system.actorOf(ContainerCmdActor.props(config), "ContainerCmdActor")
  system.actorOf(Props(classOf[Terminator], a), "terminator")

  // For startup errors
  private def error(msg: String) {
    println(msg)
    System.exit(1)
  }
}

// The main actor for this application
object ContainerCmdActor {
  def props(config: Config): Props = Props(classOf[ContainerCmdActor], config)
}

class ContainerCmdActor(config: Config) extends Actor with ActorLogging {

  override def receive: Receive = {
    case actorRef: ActorRef ⇒ log.info(s"Started $actorRef")
    case x                  ⇒ log.error(s"Received unexpected message $x")
  }
}
