package org.tmt.csw.cmd.akka

import akka.actor._
import org.tmt.csw.cmd.core._
import org.tmt.csw.cmd.akka.CommandQueueActor.ConfigQueueStatus
import java.net.URI
import org.tmt.csw.ls.LocationServiceActor.ServiceId
import org.tmt.csw.ls.LocationService
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.tmt.csw.cmd.akka.CommandStatusActor.StatusUpdate

object CommandServiceActor {
  // Child actor names
  val configDistributorActorName = "configDistributorActor"
  val commandQueueActorName = "commandQueueActor"
  val configRegistrationActorName = "configRegistrationActor"
  val commandStatusActorName = "commandStatusActor"
  val commandQueueControllerActorName = "commandQueueControllerActor"

  sealed trait CommandServiceMessage

  /**
   * Submits a configuration without waiting for a reply. Status messages will be sent to the submitter.
   */
  object Submit {
    /**
     * Command service clients should normally use this method (only passing in the config argument).
     * @param config the configuration to submit
     * @param submitter the return address (defaults to the implicit sender defined for every actor)
     * @param ignore not used: only needed to have a different argument list than the generated apply() method
     */
    def apply(config: Configuration)(implicit submitter: ActorRef = Actor.noSender, ignore: Int = 0): Submit =
      Submit(config, submitter)
  }

  /**
   * Submit a configuration.
   * @param config the configuration
   * @param submitter the actor submitting the config (normally implicit)
   */
  case class Submit(config: Configuration, submitter: ActorRef) extends CommandServiceMessage

  /**
   * Queue bypass request configuration.
   * @param config the configuration to send
   */
  case class QueueBypassRequest(config: Configuration) extends CommandServiceMessage

  /**
   * Queue bypass request configuration with an assigned submitter and runId
   * @param config the configuration
   * @param submitter the actor submitting the config (will receive status messages)
   * @param runId the unique runId
   */
  case class QueueBypassRequestWithRunId(config: Configuration, submitter: ActorRef,
                                         runId: RunId = RunId()) extends CommandServiceMessage

  /**
   * Requests that the command service return a CommandServiceStatus object to the sender containing information
   * describing the command service.
   */
  case object StatusRequest extends CommandServiceMessage

  /**
   * Reply to StatusRequest message
   */
  case class CommandServiceStatus(name: String, ready: Boolean,
                                  queueStatus: ConfigQueueStatus,
                                  queueControllerClass: String)

  /**
   * Message sent to this actor by queue and config actors to indicate if they are ready to receive commands.
   */
  case class Ready(ready: Boolean) extends CommandServiceMessage
}

/**
 * The command service actor receives the submit command with the config
 * (or other control commands) and passes it to the command queue actor.
 *
 * The command queue actor tells the command queue controller actor that
 * there is work available.  This actor comes in various flavors so that
 * it can implement "one at a time" behavior or concurrent behavior. The
 * HDC or Assembly class can extend a trait that adds the correct queue
 * controller actor to the system. The queue controller actor also
 * receives command status info and uses that to decide when the next
 * config should be taken from the queue and passed to the "queue
 * client".  It does this by sending a "Dequeue" message to the queue
 * actor. The queue actor then sends the config to the queue client. In
 * the case of an HDC, the queue client is the config actor (For an
 * assembly it is the config distributor actor).
 *
 * When the config actor receives the submit, it performs the work and
 * then sends the status to the command status actor.
 *
 * The command status actor passes the status to subscribers (which
 * include the queue controller) and also to the original submitter of
 * the config (The sender is passed along with the submit message).
 */
trait CommandServiceActor extends Actor with ActorLogging {

  import CommandServiceActor._
  import CommandQueueActor._
  import ConfigActor._

  // actor receiving config and command status messages and passing them to subscribers
  val commandStatusActor = context.actorOf(Props[CommandStatusActor], name = commandStatusActorName)

  // Create the queue actor
  val commandQueueActor = context.actorOf(CommandQueueActor.props(commandStatusActor), name = commandQueueActorName)

  // The actor that will process the configs
  def configActor: ActorRef

  // Connect the config actor, which is defined later in a derived class, to the queue on start
  override def preStart(): Unit = {
    commandQueueActor ! CommandQueueActor.QueueClient(configActor)
  }

  // The queue controller actor (Derived classes extend a trait to define this, depending on the desired behavior)
  def commandQueueControllerActor: ActorRef

  // A name describing the queue controller (for display in the status web page)
  def commandQueueControllerType: String

  // Needed for "ask"
  private implicit val execContext = context.dispatcher

  waitForReady()

  def waitForReady(): Unit = {
    context.become(waitingForReady(queueActorReady = false, configActorReady = true))
  }

  // Initial state while waiting for queue and config actors to be ready
  def waitingForReady(queueActorReady: Boolean, configActorReady: Boolean): Receive = {
    case Ready(ready) => checkIfReady(ready, queueActorReady, configActorReady)

    case s@Submit(config, submitter) =>
      notReadyError(s, RunId(), submitter)

    case s@SubmitWithRunId(config, submitter, runId) =>
      notReadyError(s, runId, submitter)

    case s@QueueBypassRequest(config) =>
      notReadyError(s, RunId(), sender())

    case s@QueueBypassRequestWithRunId(config, submitter, runId) =>
      notReadyError(s, runId, submitter)

    case StatusRequest => handleStatusRequest(sender(), ready=false)

    case x => log.error(s"Not yet ready to receive message $x")
  }

      // Receive only the command server commands
  def receiveCommands: Receive = {
    // Queue related commands
    case Submit(config, submitter) =>
      submit(SubmitWithRunId(config, submitter))

    case s@SubmitWithRunId(config, submitter, runId) =>
      submit(s)

    case QueueBypassRequest(config) =>
      queueBypassRequest(SubmitWithRunId(config, sender(), RunId()))

    case QueueBypassRequestWithRunId(config, submitter, runId) =>
      queueBypassRequest(SubmitWithRunId(config, submitter, runId))

    case s@QueueStop => commandQueueActor forward s
    case s@QueuePause => commandQueueActor forward s
    case s@QueueStart => commandQueueActor forward s
    case s@QueueDelete(runId) => commandQueueActor forward s

    case configMessage: ConfigMessage => configActor forward configMessage

    case StatusRequest => handleStatusRequest(sender(), ready=true)

    case Ready(ready) => checkIfReady(ready, queueActorReady = true, configActorReady = true)
  }

  // Logs an error message and report an error status if we receive a command
  // when not in the ready state.
  def notReadyError(msg: AnyRef, runId: RunId, submitter: ActorRef): Unit = {
    log.error(s"Not yet ready to receive command $msg")
    commandStatusActor ! StatusUpdate(CommandStatus.Error(runId, "Not ready yet"), submitter)

  }

  /**
   * Register with the location service (which must be started as a separate process).
   * @param serviceId holds the name and service type (HCD or Assembly) of this actor
   * @param configPathOpt an optional path in a config message that this actor is interested in
   * @param httpUri an optional HTTP/REST URI for the actor (if it uses Spray, for example)
   */
  def registerWithLocationService(serviceId: ServiceId, configPathOpt: Option[String] = None,
                                  httpUri: Option[URI] = None) {
    log.info(s"Registering $serviceId ($configPathOpt) with the location service")
    LocationService.register(context.system, self, serviceId, configPathOpt, httpUri)
  }


  /**
   * Called when a command is submitted
   * @param s holds the config, runId and sender
   */
  def submit(s: SubmitWithRunId): Unit = {
    log.info(s"Submit with runId(${s.runId}) ${s.config}")
    commandQueueActor ! s
  }

  /**
   * Submits a command directly, bypassing the command queue
   * @param s holds the config, runId and sender
   */
  def queueBypassRequest(s: SubmitWithRunId): Unit = {
    configActor ! SubmitWithRunId(s.config, s.submitter, s.runId)
  }

  /**
   * Answers a request for status.
   * @param requester the actor requesting the status
   */
  def handleStatusRequest(requester: ActorRef, ready: Boolean): Unit = {
    implicit val timeout = Timeout(5.seconds)
    for {
      queueStatus  <- (commandQueueActor ? StatusRequest).mapTo[ConfigQueueStatus]
    } {
      requester ! CommandServiceStatus(self.path.name, ready, queueStatus, commandQueueControllerType)
    }
  }


  /**
   * Called when a Ready message is received: If the queue and config actors are ready, enter the ready state.
   */
  def checkIfReady(ready: Boolean, queueActorReady: Boolean, configActorReady: Boolean): Unit = {
    val queueReady = if (sender == commandQueueActor) ready else queueActorReady
    val configReady = if (sender == configActor) ready else configActorReady
    if (queueReady  && configReady) {
      log.info("Ready to receive commands")
      context.become(receive)
    } else {
      context.become(waitingForReady(queueReady, configReady))
    }

  }

}

/**
 * A command service actor that forwards parts of configs to other command service actors (HCDs or assemblies)
 */
trait AssemblyCommandServiceActor extends CommandServiceActor with ConfigDistributor

