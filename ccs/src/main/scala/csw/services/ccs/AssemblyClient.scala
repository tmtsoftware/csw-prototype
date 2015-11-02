package csw.services.ccs

import akka.actor.{ ActorRefFactory, Props, Actor, ActorRef }
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
 */
case class AssemblyWrapper(assembly: ActorRef) extends Actor {
  override def receive: Receive = {
    case s: Submit ⇒
      assembly ! s
      context.become(waitingForComplete(sender()))
  }

  def waitingForComplete(replyTo: ActorRef): Receive = {
    case s: CommandStatus ⇒
      if (s.isDone) {
        replyTo ! s
        context.stop(self)
      }
  }
}

object AssemblyWrapper {
  def props(assembly: ActorRef): Props = Props(classOf[AssemblyWrapper], assembly)
}

