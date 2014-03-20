package org.tmt.csw.apps.containerCmd

import akka.kernel.Bootable
import akka.actor._
import scala.util.Properties
import com.typesafe.config.{ConfigFactory, Config}
import java.io.File

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

  val systemName = config.getString("csw.containerCmd.systemName")
  log.info(s"XXX systemName = $systemName")

  override def receive: Receive = {
    case x => log.error(s"Received unexpected message $x")
  }
}
