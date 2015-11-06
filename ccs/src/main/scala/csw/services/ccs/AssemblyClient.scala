package csw.services.ccs

import akka.actor._
import akka.util.Timeout
import csw.services.ccs.AssemblyController._
import csw.util.cfg.Configurations.SetupConfigArg

import scala.concurrent.{ Await, Future }
import akka.pattern.ask

/**
 * A client API for assemblies that hides the actor API.
 *
 * @param assemblyController the actor for the target assembly
 * @param timeout amount of time to wait for a command to complete before reporting an error
 */
case class AssemblyClient(assemblyController: ActorRef)(implicit val timeout: Timeout, context: ActorRefFactory) {

  def submit(config: SetupConfigArg): Future[CommandStatus] = {
    val wrapper = context.actorOf(AssemblyWrapper.props(assemblyController))
    (wrapper ? Submit(config)).mapTo[CommandStatus]
  }
}

/**
 * A synchronous, blocking client API for assemblies that hides the actor API.
 *
 * @param client a client for the target assembly
 */
case class BlockingAssemblyClient(client: AssemblyClient)(implicit val timeout: Timeout, context: ActorRefFactory) {

  def submit(config: SetupConfigArg): CommandStatus = {
    Await.result(client.submit(config), timeout.duration)
  }
}

/**
 * A simple wrapper to get a single response from an assembly for a single submit
 * @param assembly the target assembly actor
 * @param submit the config to submit to the assembly
 * @param replyTo the actor that should receive the final command status
 */
case class AssemblyWrapper(assembly: ActorRef, submit: Submit, replyTo: ActorRef) extends Actor with ActorLogging {
  assembly ! submit

  override def receive: Receive = {
    case s: CommandStatus ⇒
      if (s.isDone) {
        replyTo ! s
        context.stop(self)
      }

    case x ⇒ log.error(s"Received unexpected message: $x")
  }
}

object AssemblyWrapper {
  def props(assembly: ActorRef): Props = Props(classOf[AssemblyWrapper], assembly)
}

