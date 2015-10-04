package csw.services.pkg

import akka.actor.Props
import csw.services.ccs.akka.{AssemblyCommandServiceActor, OneAtATimeCommandQueueController}

object TestAssembly {
  def props(name: String): Props = Props(classOf[TestAssembly], name)
}

// A test assembly
case class TestAssembly(name: String) extends Assembly
with AssemblyCommandServiceActor with OneAtATimeCommandQueueController with LifecycleHandler {

  // XXX TODO FIXME
//  // Get the assembly's HCDs from the location service (which must be running)
//  requestServices(List(
//    ServiceId("HCD-2A", ServiceType.HCD),
//    ServiceId("HCD-2B", ServiceType.HCD)
//  ))

  def receive: Receive = receiveCommands orElse receiveLifecycleCommands
}
