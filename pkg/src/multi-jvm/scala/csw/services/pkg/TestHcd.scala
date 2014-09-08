package csw.services.pkg

import akka.actor.Props
import csw.services.cmd.akka._
import csw.services.ls.LocationServiceActor
import LocationServiceActor._

object TestHcd {
  def props(name: String, configPath: String): Props = Props(classOf[TestHcd], name, configPath)
}

case class TestHcd(name: String, configPath: String) extends Hcd with CommandServiceActor
  with OneAtATimeCommandQueueController {

  override val configActor = context.actorOf(TestConfigActor.props(commandStatusActor, 3), name)

  override def receive: Receive = receiveComponentMessages orElse receiveCommands

  val serviceId = ServiceId(name, ServiceType.HCD)
  registerWithLocationService(serviceId, Some(configPath))

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