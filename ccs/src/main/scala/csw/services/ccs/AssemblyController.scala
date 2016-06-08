package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.Timeout
import csw.services.loc.LocationService.{Location, ResolvedAkkaLocation}
import csw.services.loc.LocationTrackerClientActor
import csw.util.akka.PublisherActor
import csw.util.config.StateVariable._
import csw.util.config.Configurations.{ControlConfigArg, ObserveConfigArg, SetupConfigArg}
import csw.util.config.{RunId, StateVariable}

import scala.concurrent.duration._

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
   *
   * @param reason a description of why the config is invalid
   */
  case class Invalid(reason: String) extends Validation {
    override def isValid: Boolean = false
  }

}

/**
 * Base trait for an assembly controller actor that reacts immediately to SetupConfigArg messages.
 */
trait AssemblyController extends LocationTrackerClientActor with PublisherActor[CurrentStates] {
  this: Actor with ActorLogging ⇒

  import AssemblyController._

  // Optional actor waiting for current HCD states to match demand states
  private var stateMatcherActor: Option[ActorRef] = None

  /**
   * Receive actor messages
   */
  protected def controllerReceive: Receive = publisherReceive orElse trackerClientReceive orElse {
    case Submit(config) ⇒ submit(allResolved, config, oneway = false, sender())

    case OneWay(config) ⇒ submit(allResolved, config, oneway = true, sender())
  }

  /**
   * Called for Submit messages
   *
   * @param locationsResolved indicates if all the Assemblies connections are resolved
   * @param config            the config received
   * @param oneway            true if no completed response is needed
   * @param replyTo           actorRef of the actor that submitted the config
   */
  private def submit(
    locationsResolved: Boolean, config: ControlConfigArg, oneway: Boolean, replyTo: ActorRef
  ): Unit = {
    val statusReplyTo = if (oneway) None else Some(replyTo)
    val valid = config match {
      case sc: SetupConfigArg   ⇒ setup(locationsResolved, sc, statusReplyTo)
      case ob: ObserveConfigArg ⇒ observe(locationsResolved, ob, statusReplyTo)
    }
    valid match {
      case Valid ⇒
        replyTo ! CommandStatus.Accepted(config.info.runId)
      case Invalid(reason) ⇒
        replyTo ! CommandStatus.Error(config.info.runId, reason)
    }
  }

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param locationsResolved indicates if all the Assemblies connections are resolved
   * @param configArg         contains a list of setup configurations
   * @param replyTo           if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def setup(locationsResolved: Boolean, configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Validation = Valid

  /**
   * Called to process the observe config and reply to the given actor with the command status.
   *
   * @param locationsResolved indicates if all the Assemblies connections are resolved
   * @param configArg         contains a list of observe configurations
   * @param replyTo           if defined, the actor that should receive the final command status.
   * @return a validation object that indicates if the received config is valid
   */
  protected def observe(locationsResolved: Boolean, configArg: ObserveConfigArg,
                        replyTo: Option[ActorRef]): Validation = Valid

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
    replyTo.foreach { actorRef ⇒
      // Wait for the demand states to match the current states, then reply to the sender with the command status
      val props = HcdStatusMatcherActor.props(demandStates.toList, hcds, actorRef, runId, timeout, matcher)
      stateMatcherActor = Some(context.actorOf(props))
    }
  }

  /**
   * This method can be called from the setup method to distribute parts of the configs to HDCs based on the
   * prefix. If the prefix of a SetupConfig matches the one for the HCD, it is sent to that HCD.
   *
   * @param locationsResolved true if the locations of all the assembly's required services (HCDs) have been resolved
   * @param configArg contains one or more configs
   * @param replyTo send the command status (Completed) to this actor when all the configs are "matched" or an error status if a timeout occurs
   * @return Valid if locationsResolved, otherwise Invalid
   */
  protected def distributeSetupConfigs(locationsResolved: Boolean, configArg: SetupConfigArg,
                                       replyTo: Option[ActorRef]): Validation = {
    if (locationsResolved) {
      val pairs = for {
        config ← configArg.configs
        actorRef ← getActorRefs(config.prefix)
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
      Invalid(s)
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
      case r @ ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) ⇒ actorRefOpt
    }
    val hcds = x.flatten
    hcds.foreach(_ ! PublisherActor.Subscribe)
  }

}
