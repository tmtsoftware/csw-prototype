package org.tmt.csw.pkg

import akka.actor.Props
import org.tmt.csw.cmd.akka.OneAtATimeCommandQueueController

object TestHcd {
  def props(name: String, configPath: String): Props = Props(classOf[TestHcd], name, configPath)
}

case class TestHcd(name: String, configPath: String) extends Hcd with OneAtATimeCommandQueueController {

  override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, 3), name)

  override def receive: Receive = receiveHcdMessages

  registerWithLocationService(Some(configPath))

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
