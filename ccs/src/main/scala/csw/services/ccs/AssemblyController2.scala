package csw.services.ccs

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import csw.services.loc.LocationServiceProvider.{Location, ResolvedAkkaLocation}
import csw.services.loc.LocationTrackerClientActor3
import csw.services.log.PrefixedActorLogging
import csw.util.akka.PublisherActor
import csw.util.config.Configurations.{ControlConfigArg, ObserveConfigArg, SetupConfig, SetupConfigArg}
import csw.util.config.StateVariable._
import csw.util.config.{RunId, StateVariable}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AssemblyController2 {

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
   * Message to make a request of the assembly.
   * The assembly should reply with a Response(SetupConfig) containing the reply to the request.
   *
   * @param config describes the request
   */
  case class Request(config: SetupConfig) extends AssemblyControllerMessage

  /**
   * Response to a Request message.
   *
   * @param status set to the completion status of the request (Invalid, RequestFaild, RequestCompleted)
   * @param resp   if valid, the body of the response to the Request message
   */
  case class RequestResult(status: RequestStatus, resp: Option[SetupConfig])

  /*
  /**
   * Base trait for the results of validating incoming configs
   */
  sealed trait Validation {
    def isValid: Boolean = true
  }

  /**
   * Indicates a valid input config
   */
  case object Valid extends Validation

  /**
   * Indicates an invalid input config
   *
   * @param reason a description of why the config is invalid
   */
  case class Invalid(reason: String) extends Validation {
    override def isValid: Boolean = false
  }
*/
  /**
   * The completion status of a request
   */
  sealed trait RequestStatus {
    def isSuccess: Boolean = true
  }

  /**
   * The request completed successfully
   */
  case object RequestOK extends RequestStatus

  /**
   * The request failed
   *
   * @param reason the reason for the failure
   */
  case class RequestFailed(reason: String) extends RequestStatus {
    override def isSuccess: Boolean = false
  }

}

/**
 * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
 */
trait AssemblyController2 extends PublisherActor[CurrentStates] with LocationTrackerClientActor3 {
  this: Actor with PrefixedActorLogging =>

  import Validation._
  import AssemblyController2._
  import context.dispatcher

  // Optional actor waiting for current HCD states to match demand states
  private var stateMatcherActor: Option[ActorRef] = None

  /**
   * Receive actor messages
   */
  protected def controllerReceive: Receive = publisherReceive orElse {
    case Submit(configArg) => submit(configArg, oneway = false, sender())

    case OneWay(configArg) => submit(configArg, oneway = true, sender())

    case Request(config) =>
      val replyTo = sender()
      request(config).onComplete {
        case Success(resp) =>
          replyTo ! resp
        case Failure(ex) =>
          replyTo ! RequestResult(RequestFailed(ex.toString), None)
      }
  }

  /**
   * Called for Submit messages
   *
   * @param config  the config received
   * @param oneway  true if no completed response is needed
   * @param replyTo actorRef of the actor that submitted the config
   */
  private def submit(config: ControlConfigArg, oneway: Boolean, replyTo: ActorRef): Unit = {
    val statusReplyTo = if (oneway) None else Some(replyTo)
    val valid = config match {
      case sc: SetupConfigArg   => setup(sc, statusReplyTo)
      case ob: ObserveConfigArg => observe(ob, statusReplyTo)
    }
    valid match {
      case Valid =>
        replyTo ! CommandStatus.Accepted(config.info.runId)
      case Invalid(problem) =>
        replyTo ! CommandStatus.Error(config.info.runId, problem.reason)
    }
  }

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param configArg contains a list of setup configurations
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def setup(configArg: SetupConfigArg, replyTo:   Option[ActorRef]): Validation = Valid

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param configArg contains a list of observe configurations
   * @param replyTo   if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def observe(configArg: ObserveConfigArg, replyTo: Option[ActorRef]): Validation = Valid

  /**
   * Convenience method that can be used to monitor a set of state variables and reply to
   * the given actor when they all match the demand states, or reply with an error if
   * there is a timeout.
   *
   * @param demandStates list of state variables to be matched (wait until current state matches demand)
   * @param hcds         the target HCD actors
   * @param replyTo      actor to receive CommandStatus.Completed or CommandStatus.Error("timeout...") message
   * @param runId        runId to include in the command status message sent to the replyTo actor
   * @param timeout      amount of time to wait for states to match (default: 60 sec)
   * @param matcher      matcher to use (default: equality)
   */
  protected def matchDemandStates(demandStates: Seq[DemandState], hcds: Set[ActorRef], replyTo: Option[ActorRef], runId: RunId,
                                  timeout: Timeout = Timeout(60.seconds),
                                  matcher: Matcher = StateVariable.defaultMatcher): Unit = {
    // Cancel any previous state matching, so that no timeout errors are sent to the replyTo actor
    stateMatcherActor.foreach(context.stop)
    replyTo.foreach { actorRef =>
      // Wait for the demand states to match the current states, then reply to the sender with the command status
      val props = HcdStatusMatcherActor.props(demandStates.toList, hcds, actorRef, runId, timeout, matcher, this.prefix)
      stateMatcherActor = Some(context.actorOf(props))
    }
  }

  protected def getActorRefs(targetPrefix: String): Set[ActorRef] = {
    val x = getLocations.collect {
      case r @ ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) if prefix == targetPrefix => actorRefOpt
    }
    x.flatten
  }
  /**
   * This method can be called from the setup method to distribute parts of the configs to HCDs based on the
   * prefix. If the prefix of a SetupConfig matches the one for the HCD, it is sent to that HCD.
   *
   * @param locationsResolved true if the locations of all the assembly's required services (HCDs) have been resolved
   * @param configArg         contains one or more configs
   * @param replyTo           send the command status (Completed) to this actor when all the configs are "matched" or an error status if a timeout occurs
   * @return Valid if locationsResolved, otherwise Invalid
   */

  protected def distributeSetupConfigs(locationsResolved: Boolean, configArg: SetupConfigArg,
                                       replyTo: Option[ActorRef]): Validation = {
    if (locationsResolved) {
      val pairs = for {
        config <- configArg.configs
        actorRef <- getActorRefs(config.prefix)
      } yield {
        log.debug(s"Forwarding $config to $actorRef")
        actorRef ! HcdController.Submit(config)
        (actorRef, DemandState(config))
      }
      val hcds = pairs.map(_._1).toSet
      val demandStates = pairs.map(_._2)
      matchDemandStates(demandStates, hcds, replyTo, configArg.info.runId)
      Valid
    } else {
      val s = "Unresolved locations for one or more HCDs"
      log.error(s)
      Invalid(UnresolvedLocationsIssue(s))
    }
  }

  /**
   * Helper method to subscribe to status values (CurrentState objects) from the assembly's connections (to HCDs).
   * This can be called from the allResolved() method.
   *
   * @param locations  the resolved locations of the assembly's connections (to HCDs, for example)
   * @param subscriber the actor that should receive the status messages (CurrentState objects)
   */
  protected def subscribe(locations: Set[Location], subscriber: ActorRef = self): Unit = {
    val x = locations.collect {
      case r @ ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) => actorRefOpt
    }
    val hcds = x.flatten
    hcds.foreach(_ ! PublisherActor.Subscribe)
  }

  /**
   * Called for Request messages. Derived classes should override this method to handle
   * Request messages. the default is to return an error response.
   *
   * @param config the request
   * @return a future response, which will be sent to the sender of the request
   */
  protected def request(config: SetupConfig): Future[RequestResult] = {
    Future.successful(RequestResult(RequestFailed("Assembly controller 'request' method not implemented"), None))
  }

}
