package csw.services.ccs

import akka.actor.{ ActorRef, ActorLogging, Actor }
import csw.services.loc.LocationService.{ ResolvedService, Disconnected, ServicesReady }
import csw.services.loc.ServiceRef
import csw.shared.cmd.CommandStatus
import csw.util.cfg.Configurations.{ ObserveConfigArg, SetupConfigArg, ControlConfigArg }

/**
 * Defines the Assembly controller actor messages
 */
object AssemblyController {

  /**
   * Base trait of all received messages
   */
  sealed trait AssemblyControllerMessage

  /**
   * Message to submit a configuration to the assembly.
   * The sender will receive CommandStatus messages.
   * If the config is valid, a Accepted message is sent, otherwise an Error.
   * When the work for the config has been completed, a Completed message is sent
   * (or an Error message, if an error occurred).
   *
   * @param config the configuration to execute
   */
  case class Submit(config: ControlConfigArg) extends AssemblyControllerMessage

  /**
   * Message to submit a oneway config to the assembly.
   * In this case, the sender will receive only an Accepted (or Error) message,
   * indicating that config is valid (or invalid).
   * There will be no messages on completion.
   *
   * @param config the configuration to execute
   */
  case class OneWay(config: ControlConfigArg) extends AssemblyControllerMessage

  /**
   * Return value for validate method
   */
  sealed trait Validation {
    def isValid: Boolean = true
  }

  /**
   * Indicates a valid config
   */
  case object Valid extends Validation

  /**
   * Indicates an invalid config
   * @param reason a description of why the config is invalid
   */
  case class Invalid(reason: String) extends Validation {
    override def isValid: Boolean = false
  }

}

/**
 * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
 */
trait AssemblyController extends Actor with ActorLogging {

  import AssemblyController._

  override def receive = waitingForServices

  /**
   * Receive state while waiting for required services
   */
  private def waitingForServices: Receive = additionalReceive orElse {

    case Submit(config) ⇒
      notReady(config)

    case ServicesReady(map) ⇒
      connected(map)
      context.become(ready(map))

    case Disconnected ⇒

    case x            ⇒ log.warning(s"Received unexpected message: $x")
  }

  /**
   * Receive state while required services are available
   */
  private def ready(services: Map[ServiceRef, ResolvedService]): Receive = additionalReceive orElse {
    case Submit(config)     ⇒ submit(services, config, oneway = false, sender())
    case OneWay(config)     ⇒ submit(services, config, oneway = true, sender())

    case ServicesReady(map) ⇒ context.become(ready(map))

    case Disconnected       ⇒ context.become(waitingForServices)

    case x                  ⇒ log.warning(s"Received unexpected message: $x")
  }

  /**
   * Called for Submit messages
   * @param services map of the required services
   * @param config the config received
   * @param oneway true if no completed response is needed
   * @param replyTo actorRef of the actor that submitted the config
   */
  private def submit(services: Map[ServiceRef, ResolvedService],
                     config: ControlConfigArg, oneway: Boolean, replyTo: ActorRef): Unit = {

    val statusReplyTo = if (oneway) None else Some(replyTo)
    val valid = config match {
      case sc: SetupConfigArg ⇒
        setup(services, sc, statusReplyTo)
      case ob: ObserveConfigArg ⇒
        observe(services, ob, statusReplyTo)
    }
    valid match {
      case Valid ⇒
        replyTo ! CommandStatus.Accepted(config.info.runId)
      case Invalid(reason) ⇒
        replyTo ! CommandStatus.Error(config.info.runId, reason)
    }
  }

  /**
   * Replies with an error message if we receive a config when not in the ready state
   * @param config the received config
   */
  private def notReady(config: ControlConfigArg): Unit = {
    val s = s"Ignoring config since services not connected: $config"
    log.warning(s)
    sender() ! CommandStatus.Error(config.info.runId, s)

  }

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param services contains information about any required services
   * @param configArg contains a list of setup configurations
   * @param replyTo if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def setup(services: Map[ServiceRef, ResolvedService], configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Validation

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param services contains information about any required services
   * @param configArg contains a list of observe configurations
   * @param replyTo if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def observe(services: Map[ServiceRef, ResolvedService], configArg: ObserveConfigArg,
                        replyTo: Option[ActorRef]): Validation = Valid

  /**
   * Derived classes and traits can extend this to accept additional messages
   */
  protected def additionalReceive: Receive

  /**
   * Derived classes and traits can extend this to be notified when required services are ready
   * @param services maps serviceRef to information that includes the host, port and actorRef
   */
  protected def connected(services: Map[ServiceRef, ResolvedService]): Unit = {}

  /**
   * Derived classes and traits can extend this to be notified when required services are disconnected
   */
  protected def disconnected(): Unit = {}

}
