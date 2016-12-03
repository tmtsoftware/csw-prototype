package csw.services.ccs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.AssemblyController._
import csw.services.ccs.CommandStatus.CommandResult
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

  //  /**
  //   * Returns a future object containing the current state of the assembly.
  //   */
  //  def configGet(): Future[CurrentState] = {
  //    (assemblyController ? RequestCurrent).mapTo[CurrentState]
  //  }
  //
  //  /**
  //   * Makes a request of the assembly and returns the result
  //   * @param config describes the request
  //   * @return the future result returned from the assembly
  //   */
  //  def request(config: SetupConfig): Future[RequestResult] = {
  //    (assemblyController ? Request(config)).mapTo[RequestResult]
  //  }
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

  //  /**
  //   * Returns an object containing the current state of the assembly
  //   */
  //  def configGet(): CurrentState = {
  //    Await.result(client.configGet(), timeout.duration)
  //  }
  //
  //  /**
  //   * Makes a request of the assembly and returns the result
  //   * @param config describes the request
  //   * @return the future result returned from the assembly
  //   */
  //  def request(config: SetupConfig): RequestResult = {
  //    Await.result(client.request(config), timeout.duration)
  //  }
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
      assembly ! s
      context.become(waitingForResult(sender()))

    case x => log.error(s"Received unexpected message: $x")
  }

  def waitingForResult(replyTo: ActorRef): Receive = {
    case r: CommandResult =>
      replyTo ! r
      context.stop(self)

    case _ =>
  }
}

