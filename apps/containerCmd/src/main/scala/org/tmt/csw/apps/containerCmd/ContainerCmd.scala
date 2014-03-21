package org.tmt.csw.apps.containerCmd

import akka.kernel.Bootable
import akka.actor._
import scala.util.Properties
import com.typesafe.config.{ConfigFactory, Config}
import java.io.File
import scala.collection.JavaConversions._
import org.tmt.csw.pkg.Container

/**
 * A command line application for creating containers with components specified in a config file.
 * This class is started by the Akka microkernel in standalone mode.
 *
 * The name of the config file is expected to be passed as a VM option: -DcontainerCmd.config=myFile.conf.
 */
class ContainerCmd extends Bootable {
  val system = ActorSystem("system")

  def startup(): Unit = {
    val configProp = "containerCmd.config"
    Properties.propOrNone(configProp) match {
      case Some(configFile) =>
        val file = new File(configFile)
        if (!file.exists()) {
          error(s"File '$configFile' does not exist")
        }
        val config = ConfigFactory.parseFileAnySyntax(file)
        system.actorOf(ContainerCmdActor.props(config), "ContainerCmdActor")
      case None =>
        error(s"Please specify the config file using: -D$configProp=myFile.conf")
    }
  }

  def error(msg: String) {
    println(msg)
    shutdown()
  }

  def shutdown(): Unit = {
    system.shutdown()
  }
}

// The main actor for this application
object ContainerCmdActor {
  def props(config: Config): Props = Props(classOf[ContainerCmdActor], config)
}

class ContainerCmdActor(config: Config) extends Actor with ActorLogging {

  parseConfig()

  // Parses the config file pass in via -DcontainerCmd.config and creates the
  // container, adding the components specified in the config file.
  def parseConfig(): Unit = {
    val containerName = config.getString("container.name")
    log.info(s"Create container $containerName")
    val container = Container.create(containerName)
    val components = config.getConfig("container.components")
    for(key <- components.root.keySet()) {
      val componentConfig = components.getConfig(key)
      val className = componentConfig.getString("class")
      val args =
        if (componentConfig.hasPath("args"))
          componentConfig.getList("args").toList.map(_.unwrapped().toString)
        else List()
      log.info(s"XXX args = $args")
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
