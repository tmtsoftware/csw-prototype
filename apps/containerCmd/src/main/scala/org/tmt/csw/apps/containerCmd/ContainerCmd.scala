package org.tmt.csw.apps.containerCmd

import akka.actor._
import com.typesafe.config.{ConfigFactory, Config}
import java.io.File
import scala.collection.JavaConversions._
import org.tmt.csw.pkg.Container
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A command line application for creating containers with components specified in a config file.
 */
object ContainerCmd {
  val log = Logger(LoggerFactory.getLogger("ContainerCmd"))

  // Main: usage: containerCmd configFile
  def main(args: Array[String]): Unit = {
    if (args.length != 1) error("Expected a config file argument")
    val configFile = args(0)
    val file = new File(configFile)
    if (!file.exists()) {
      error(s"File '$configFile' does not exist")
    }
    val config = ConfigFactory.parseFileAnySyntax(file)
    val system = ActorSystem("ContainerCmd")
    val a = system.actorOf(ContainerCmdActor.props(config), "ContainerCmdActor")
    system.actorOf(Props(classOf[Terminator], a), "terminator")
  }

  // For startup errors
  def error(msg: String) {
    println(msg)
    System.exit(1)
  }

  // Exits the application when the main actor stops
  class Terminator(ref: ActorRef) extends Actor with ActorLogging {
    context watch ref

    def receive = {
      case Terminated(_) =>
        log.info("{} has terminated, shutting down system", ref.path)
        context.system.shutdown()
    }
  }

}

// The main actor for this application
object ContainerCmdActor {
  def props(config: Config): Props = Props(classOf[ContainerCmdActor], config)
}

class ContainerCmdActor(config: Config) extends Actor with ActorLogging {

  parseConfig()

  // Parses the config file argument and creates the
  // container, adding the components specified in the config file.
  def parseConfig(): Unit = {
    val containerName = config.getString("container.name")
    log.info(s"Create container $containerName")
    val container = Container.create(containerName)
    val components = config.getConfig("container.components")
    for (key <- components.root.keySet()) {
      val componentConfig = components.getConfig(key)
      val className = componentConfig.getString("class")
      val args =
        if (componentConfig.hasPath("args"))
          componentConfig.getList("args").toList.map(_.unwrapped().toString)
        else List()
      log.info(s"Create component with class $className and args $args")
      val props = Props(Class.forName(className), args: _*)
      container ! Container.CreateComponent(props, key)
    }
  }

  override def receive: Receive = {
    case actorRef: ActorRef => log.info(s"Started $actorRef")
    case x => log.error(s"Received unexpected message $x")
  }
}
