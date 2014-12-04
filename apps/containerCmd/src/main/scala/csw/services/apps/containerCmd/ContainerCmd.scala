package csw.services.apps.containerCmd

import java.io.File

import akka.actor._
import com.typesafe.config.{ Config, ConfigFactory }
import csw.services.ls.LocationService.RegInfo
import csw.services.ls.LocationServiceActor.{ ServiceType, ServiceId }
import csw.services.pkg.Container

import scala.collection.JavaConversions._
import csw.util.akka.Terminator
import java.net.URI

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

  parseConfig()

  override def receive: Receive = {
    case actorRef: ActorRef ⇒ log.info(s"Started $actorRef")
    case x                  ⇒ log.error(s"Received unexpected message $x")
  }

  // Parses the config file argument and creates the
  // container, adding the components specified in the config file.
  private def parseConfig(): Unit = {
    val containerName = config.getString("container.name")
    log.info(s"Create container $containerName")
    val container = Container.create(containerName)
    val components = config.getConfig("container.components")
    for (key ← components.root.keySet()) {
      val componentConfig = components.getConfig(key)
      val className = componentConfig.getString("class")
      val args =
        if (componentConfig.hasPath("args"))
          componentConfig.getList("args").toList.map(_.unwrapped().toString)
        else List()
      log.info(s"Create component with class $className and args $args")
      val props = Props(Class.forName(className), args: _*)
      val serviceType = getServiceType(componentConfig.getString("type"))
      val serviceId = ServiceId(key, serviceType)
      val configPath = if (componentConfig.hasPath("path")) Some(componentConfig.getString("path")) else None
      val uri = if (componentConfig.hasPath("uri")) Some(new URI(componentConfig.getString("uri"))) else None
      val regInfo = RegInfo(serviceId, configPath, uri)
      container ! Container.CreateComponent(props, regInfo)
    }
  }

  private def getServiceType(typeString: String): ServiceType = {
    if (typeString == "Assembly") ServiceType.Assembly
    else if (typeString == "HCD") ServiceType.HCD
    else ServiceType.Service
  }
}
