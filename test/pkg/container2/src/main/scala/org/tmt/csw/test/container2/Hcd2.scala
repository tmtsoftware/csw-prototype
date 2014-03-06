package org.tmt.csw.test.container2

import akka.actor.Props
import org.tmt.csw.pkg.Hcd
import org.tmt.csw.cmd.akka.OneAtATimeCommandQueueController

// A test HCD that is configured with the given name and config path
object Hcd2 {
  def props(name: String, configPath: String): Props = Props(classOf[Hcd2], name, configPath)
}

case class Hcd2(name: String, configPath: String) extends Hcd with OneAtATimeCommandQueueController {

  val configKey = configPath.split('.').last
  override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, configKey, 3), name)

  // Register with the location service (which must be started as a separate process)
  registerWithLocationService(Some(configPath))

  override def receive: Receive = receiveHcdMessages orElse {
    case x => println(s"XXX HCD2: Received unknown message: $x")
  }

  // -- Implement Component methods --
  override def initialize(): Unit = {
    log.info(s"$name initialize")
  }

  override def startup(): Unit = {
    log.info(s"$name startup")
  }

  override def run(): Unit = {
    log.info(s"$name run")
  }

  override def shutdown(): Unit = {
    log.info(s"$name shutdown")
  }

  override def uninit(): Unit = {
    log.info(s"$name uninit")
  }

  override def remove(): Unit = {
    log.info(s"$name remove")
  }
}
