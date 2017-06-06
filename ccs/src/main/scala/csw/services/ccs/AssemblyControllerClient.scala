package csw.services.ccs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.AssemblyController._
import csw.services.ccs.CommandStatus._
import csw.util.itemSet.ItemSets.Setup

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
  def submit(config: Setup): Future[CommandResponse] = {
    val wrapper = context.actorOf(AssemblyWrapper.props(assemblyController))
    (wrapper ? Submit(config)).mapTo[CommandResponse]
  }
}

// --

/**
 * A synchronous, blocking client API for assemblies that hides the actor API.
 *
 * @param client a client for the target assembly
 */
case class BlockingAssemblyClient(client: AssemblyControllerClient)(implicit val timeout: Timeout, context: ActorRefFactory) {

  def submit(config: Setup): CommandResponse = {
    Await.result(client.submit(config), timeout.duration)
  }
}

// --

private object AssemblyWrapper {
  def props(assembly: ActorRef): Props =
    Props(new AssemblyWrapper(assembly))
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
    case cr: CommandResponse =>
      cr match {
        case Accepted =>
          context.become(waitingForResult(replyTo))
        case x =>
          replyTo ! x
      }

    case _ =>
  }

  def waitingForResult(replyTo: ActorRef): Receive = {
    case r: CommandResponse =>
      replyTo ! r
      context.stop(self)
    case _ =>
  }
}
