package csw.services.pkg

import csw.services.ccs.DistributorController

// A test assembly that just forwards configs to HCDs based on prefix
case class TestAssembly(name: String) extends Assembly with DistributorController with LifecycleHandler {
  def receive: Receive = receiveCommands orElse receiveLifecycleCommands
}
