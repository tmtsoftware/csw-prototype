package csw.services.ccs

import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.util.Timeout
import csw.services.loc.LocationService.{ ResolvedService, Disconnected, ServicesReady }
import csw.services.loc.ServiceRef
import csw.util.cfg.Configurations.StateVariable._
import csw.util.cfg.Configurations.{ StateVariable, ObserveConfigArg, SetupConfigArg, ControlConfigArg }
import csw.util.cfg.RunId
import scala.concurrent.duration._

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

  // Optional actor waiting for current state variables to match demand states
  private var stateMatcherActor: Option[ActorRef] = None

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

  /**
   * Convenience method that can be used to monitor a set of state variables and reply to
   * the given actor when they all match the demand states, or reply with an error if
   * there is a timeout.
   *
   * @param demandStates list of state variables to be matched (wait until current state matches demand)
   * @param replyTo actor to receive CommandStatus.Completed or CommandStatus.Error("timeout...") message
   * @param runId runId to include in the command status message sent to the replyTo actor
   * @param timeout amount of time to wait for states to match (default: 60 sec)
   * @param matcher matcher to use (default: equality)
   */
  protected def matchDemandStates(demandStates: Seq[DemandState], replyTo: Option[ActorRef], runId: RunId,
                                  timeout: Timeout = Timeout(60.seconds),
                                  matcher: Matcher = StateVariable.defaultMatcher): Unit = {
    // Cancel any previous state matching, so that no timeout errors are sent to the replyTo actor
    stateMatcherActor.foreach(context.stop)
    replyTo.foreach { actorRef ⇒
      // Wait for the demand states to match the current states, then reply to the sender with the command status
      val props = StateMatcherActor.props(demandStates.toList, actorRef, runId, timeout, matcher)
      stateMatcherActor = Some(context.actorOf(props))
    }
  }

}
