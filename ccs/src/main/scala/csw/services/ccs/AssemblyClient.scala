package csw.services.ccs

import akka.actor.ActorRef
import akka.util.Timeout
import csw.shared.cmd.CommandStatus
import csw.util.cfg.Configurations.SetupConfigArg

import scala.concurrent.{ Await, Future }
import akka.pattern.ask

/**
 * A client API for assemblies that hides the actor API.
 *
 * @param assemblyController the actor for the target assembly
 * @param timeout amount of time to wait for a command to complete before reporting an error
 */
case class AssemblyClient(assemblyController: ActorRef)(implicit val timeout: Timeout) {

  def submit(config: SetupConfigArg): Future[CommandStatus] = {
    (assemblyController ? config).mapTo[CommandStatus]
  }
}

/**
 * A synchronous, blocking client API for assemblies that hides the actor API.
 *
 * @param client a client for the target assembly
 */
case class BlockingAssemblyClient(client: AssemblyClient)(implicit val timeout: Timeout) {

  def submit(config: SetupConfigArg): CommandStatus = {
    Await.result(client.submit(config), timeout.duration)
  }
}
