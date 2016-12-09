package csw.services.ccs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.AssemblyController._
import csw.services.ccs.CommandStatus._
import csw.util.config.Configurations.SetupConfigArg

import scala.concurrent.{Await, Future}

/**
 * A client API for assemblies that hides the actor API.
 *
 * @param assemblyController the actor for the target assembly
 * @param timeout amount of time to wait for a command to complete before reporting an error
 */
case class AssemblyControllerClient(assemblyController: ActorRef)(implicit val timeout: Timeout, context: ActorRefFactory) {

  /**
   * Submits the given config to the assembly and returns the future result
   * (Note: This assumes that the assembly will return a CommandResult for the given config)
   */
  def submit(config: SetupConfigArg): Future[CommandResult] = {
    val wrapper = context.actorOf(AssemblyWrapper.props(assemblyController))
    (wrapper ? Submit(config)).mapTo[CommandResult]
  }
}

// --

/**
 * A synchronous, blocking client API for assemblies that hides the actor API.
 *
 * @param client a client for the target assembly
 */
case class BlockingAssemblyClient(client: AssemblyControllerClient)(implicit val timeout: Timeout, context: ActorRefFactory) {

  def submit(config: SetupConfigArg): CommandResult = {
    Await.result(client.submit(config), timeout.duration)
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
    case s: Submit =>
      log.info(s"Sending: $s")
      assembly ! s
      context.become(waitingForAccept(sender()))

    case x => log.error(s"Received unexpected message: $x")
  }

  def waitingForAccept(replyTo: ActorRef): Receive = {
    case cr: CommandResult =>
      cr.overall match {
        case Accepted =>
          println(s"Received accepted")
          context.become(waitingForResult(replyTo))
        case NotAccepted | AllCompleted | Incomplete =>
          println(s"Received not accepted")
          replyTo ! cr
      }

    case _ =>
  }

  def waitingForResult(replyTo: ActorRef): Receive = {
    case r: CommandResult =>
      println(s"Received final")
      replyTo ! r
      context.stop(self)

    case _ =>
  }
}
