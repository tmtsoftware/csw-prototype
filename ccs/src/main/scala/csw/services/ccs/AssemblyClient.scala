package csw.services.ccs

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.AssemblyController._
import csw.services.kvs.{KvsSettings, StateVariableStore}
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

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
   * Returns the given config with the values updated to reflect the actual
   * settings in the assembly.
   *
   * @param config sample config, the values are ignored, only the keys are important here
   * @return the config, with current values from the assembly (on error, an empty config is returned)
   */
  def configGet(config: SetupConfigArg): Future[SetupConfigArg] = {
    val wrapper = context.actorOf(ConfigGetActor.props())
    (wrapper ? config).mapTo[SetupConfigArg]
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
   * Returns the given config with the values updated to reflect the actual
   * settings in the assembly.
   *
   * @param config a sample config (only the prefixes are important)
   * @return the config, with current values from the assembly (on error, an empty config is returned)
   */
  def configGet(config: SetupConfigArg): SetupConfigArg = {
    Await.result(client.configGet(config), timeout.duration)
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

  var replyTo: Option[ActorRef] = None
  context.become(waitingForSubmit)

  override def receive: Receive = Actor.emptyBehavior

  def waitingForSubmit: Receive = {
    case s: Submit ⇒
      replyTo = Some(sender())
      assembly ! s
      context.become(waitingForStatus)

    case x ⇒ log.error(s"Received unexpected message: $x")
  }

  def waitingForStatus: Receive = {
    case s: CommandStatus ⇒
      if (s.isDone) {
        replyTo.foreach(_ ! s)
        context.stop(self)
      }

    case x ⇒ log.error(s"Received unexpected message: $x")
  }
}

// --

private object ConfigGetActor {
  def props(): Props = Props(classOf[ConfigGetActor])

}

// Actor that gets updated config values from the state variable store
private class ConfigGetActor extends Actor with ActorLogging {
  import context.dispatcher

  override def receive: Receive = {
    case s: SetupConfigArg ⇒ submit(sender(), s)

    case x                 ⇒ log.error(s"Received unexpected message: $x")
  }

  // Gets the values associated with the configArg and replies to the given actor
  // with an updated SetupConfigArg
  def submit(replyTo: ActorRef, configArg: SetupConfigArg): Unit = {
    val settings = KvsSettings(context.system)
    val svs = StateVariableStore(settings)
    Future.sequence(configArg.configs.map(c ⇒ svs.get(c.prefix))).onComplete {
      case Success(seq) ⇒
        val configs = seq.flatten.map(s ⇒ SetupConfig(s.configKey, s.data))
        replyTo ! SetupConfigArg(configArg.info, configs: _*)
        context.stop(self)
      case Failure(ex) ⇒
        log.error("Failed to get values from state variable store", ex)
        replyTo ! SetupConfigArg(configArg.info)
        context.stop(self)
    }
  }

}

