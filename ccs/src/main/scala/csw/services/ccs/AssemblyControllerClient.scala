package csw.services.ccs

import akka.actor.ActorRef
import akka.util.Timeout
import csw.shared.cmd.CommandStatus
import csw.util.config.Configurations.SetupConfigArg

import scala.concurrent.Future
import akka.pattern.ask

/**
 * A client API for assemblies that hides the actor API.
 *
 * @param assemblyController the actor for the target assembly
 * @param timeout amount of time to wait for a command to complete before reporting an error
 */
case class AssemblyControllerClient(assemblyController: ActorRef)(implicit val timeout: Timeout) {

  def submit(config: SetupConfigArg): Future[CommandStatus] = {
    (assemblyController ? config).mapTo[CommandStatus]
  }

//  def current: Future[CommandStatus] = {
//
//  }
}
