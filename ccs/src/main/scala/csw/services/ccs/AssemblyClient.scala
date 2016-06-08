package csw.services.ccs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.AssemblyController._
import csw.util.akka.PublisherActor.RequestCurrent
import csw.util.config.Configurations.SetupConfigArg
import csw.util.config.StateVariable.CurrentState

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
 * A client API for assemblies that hides the actor API.
 *
 * @param assemblyController the actor for the target assembly
 * @param timeout amount of time to wait for a command to complete before reporting an error
 */
case class AssemblyClient(assemblyController: ActorRef)(implicit val timeout: Timeout, context: ActorRefFactory) {

  /**
   * Submits the given config to the assembly and returns the future (final) command status
   */
  def submit(config: SetupConfigArg): Future[CommandStatus] = {
    val wrapper = context.actorOf(AssemblyWrapper.props(assemblyController))
    (wrapper ? Submit(config)).mapTo[CommandStatus]
  }

  /**
   * Returns a future object containing the current state of the assembly.
   */
  def configGet(): Future[CurrentState] = {
    (assemblyController ? RequestCurrent).mapTo[CurrentState]
  }
}

// --

/**
 * A synchronous, blocking client API for assemblies that hides the actor API.
 *
 * @param client a client for the target assembly
 */
case class BlockingAssemblyClient(client: AssemblyClient)(implicit val timeout: Timeout, context: ActorRefFactory) {

  def submit(config: SetupConfigArg): CommandStatus = {
    Await.result(client.submit(config), timeout.duration)
  }

  /**
   * Returns an object containing the current state of the assembly
   */
  def configGet(): CurrentState = {
    Await.result(client.configGet(), timeout.duration)
  }

}

// --

private object AssemblyWrapper {
  def props(assembly: ActorRef): Props =
    Props(classOf[AssemblyWrapper], assembly)
}

/**
 * A simple wrapper to get a single response from an assembly for a single submit
 * @param assembly the target assembly actor
 */
private case class AssemblyWrapper(assembly: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case s: Submit ⇒
      assembly ! s
      context.become(waitingForStatus(sender()))

    case x ⇒ log.error(s"Received unexpected message: $x")
  }

  def waitingForStatus(replyTo: ActorRef): Receive = {
    case s: CommandStatus ⇒
      if (s.isDone) {
        replyTo ! s
        context.stop(self)
      }

    case x ⇒ log.error(s"Received unexpected message: $x")
  }
}

