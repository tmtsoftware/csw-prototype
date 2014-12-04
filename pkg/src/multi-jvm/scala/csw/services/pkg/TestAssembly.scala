package csw.services.pkg

import akka.actor.Props
import csw.services.cmd.akka.{AssemblyCommandServiceActor, OneAtATimeCommandQueueController}
import csw.services.ls.LocationServiceActor
import LocationServiceActor.{ServiceType, ServiceId}

object TestAssembly {
  def props(name: String): Props = Props(classOf[TestAssembly], name)
}

// A test assembly
case class TestAssembly(name: String) extends Assembly with AssemblyCommandServiceActor with OneAtATimeCommandQueueController {

  // Get the assembly's HCDs from the location service (which must be running)
  requestServices(List(
    ServiceId("HCD-2A", ServiceType.HCD),
    ServiceId("HCD-2B", ServiceType.HCD)
  ))

  def receive: Receive = receiveCommands
}
