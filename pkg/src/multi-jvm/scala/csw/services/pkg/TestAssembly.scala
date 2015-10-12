package csw.services.pkg

import csw.services.ccs.AssemblyDistributorController

// A test assembly that just forwards configs to HCDs based on prefix
case class TestAssembly(name: String) extends Assembly with AssemblyDistributorController with LifecycleHandler {
  def receive: Receive = receiveCommands orElse receiveLifecycleCommands
}
