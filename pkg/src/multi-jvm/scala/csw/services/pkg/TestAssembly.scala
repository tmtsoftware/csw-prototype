package csw.services.pkg

import akka.actor.Props
import csw.services.ccs.Controller
import csw.util.config.Configurations.SetupConfig

// A test assembly
case class TestAssembly(name: String) extends Assembly with Controller with LifecycleHandler {

  def receive: Receive = receiveCommands orElse receiveLifecycleCommands

  override def process(config: SetupConfig): Unit = {
  }
}
