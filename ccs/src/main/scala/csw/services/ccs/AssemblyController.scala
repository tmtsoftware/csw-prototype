package csw.services.ccs

import akka.actor.{ ActorRef, ActorLogging, Actor }
import csw.services.loc.LocationService.{ ResolvedService, Disconnected, ServicesReady }
import csw.services.loc.ServiceRef
import csw.shared.cmd.CommandStatus
import csw.util.cfg.Configurations.{ ObserveConfigArg, SetupConfigArg, ControlConfigArg }

/**
 * Assembly controller
 */
object AssemblyController {

  /**
   * Base trait of all received messages
   */
  sealed trait AssemblyControllerMessage

  /**
   * Message to submit a config to the assembly (a setup or observe config arg)
   * The sender should receive CommandStatus messages (Accepted, Completed, etc.)
   * If oneway is true, no message needs to be sent on completion, otherwise a
   * final CommandStatus.Completed message should be sent when the command completes.
   * In any case, an Accepted message (or Error, if not accepted) should be sent to
   * the sender.
   *
   * @param config the configuration to execute
   * @param oneway if true, the sender does not need to be notified when the command completes
   */
  case class Submit(config: ControlConfigArg, oneway: Boolean = false) extends AssemblyControllerMessage

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
  def waitingForServices: Receive = additionalReceive orElse {

    case Submit(config, _) ⇒
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
  def ready(services: Map[ServiceRef, ResolvedService]): Receive = additionalReceive orElse {
    case Submit(config, oneway) ⇒ submit(services, config, oneway, sender())

    case ServicesReady(map)     ⇒ context.become(ready(map))

    case Disconnected           ⇒ context.become(waitingForServices)

    case x                      ⇒ log.warning(s"Received unexpected message: $x")
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
    validate(config) match {
      case Valid ⇒
        replyTo ! CommandStatus.Accepted(config.info.runId)
        val statusReplyTo = if (oneway) None else Some(replyTo)
        config match {
          case sc: SetupConfigArg ⇒
            setup(services, sc, statusReplyTo)
          case ob: ObserveConfigArg ⇒
            observe(services, ob, statusReplyTo)
        }
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
   * Implementing classes decide if a config can be accepted
   */
  protected def validate(config: ControlConfigArg): Validation

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param services contains information about any required services
   * @param configArg contains a list of setup configurations
   * @param replyTo if defined, the actor that should receive the final command status.
   */
  protected def setup(services: Map[ServiceRef, ResolvedService], configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Unit

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param services contains information about any required services
   * @param configArg contains a list of observe configurations
   * @param replyTo if defined, the actor that should receive the final command status.
   */
  protected def observe(services: Map[ServiceRef, ResolvedService], configArg: ObserveConfigArg,
                        replyTo: Option[ActorRef]): Unit = {}

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
