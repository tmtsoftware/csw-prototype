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

  override def receive: Receive = receiveCommands

  val serviceId = ServiceId(name, ServiceType.HCD)
  registerWithLocationService(serviceId, Some(configPath))
}
