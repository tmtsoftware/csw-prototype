package csw.services.ccs

import csw.services.ccs.Validation.{Valid, Validation}
import csw.util.config.StateVariable.{DemandState, Matcher}
import akka.actor.{ActorContext, ActorRef}
import akka.util.Timeout
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.util.config.Configurations.SetupConfigArg
import csw.util.config.{RunId, StateVariable}

import scala.concurrent.duration._

/**
 * Created by abrighto on 02/12/16.
 */
case class ConfigDistributor(context: ActorContext, locations: Set[ResolvedAkkaLocation]) {

  // Optional actor waiting for current HCD states to match demand states
  private var stateMatcherActor: Option[ActorRef] = None

  /**
   * Monitors a set of state variables and replies to the given actor when they all match the demand states,
   * or replies with an error if there is a timeout.
   *
   * @param demandStates list of state variables to be matched (wait until current state matches demand)
   * @param hcds         the target HCD actors
   * @param replyTo      actor to receive CommandStatus.Completed or CommandStatus.Error("timeout...") message
   * @param runId        runId to include in the command status message sent to the replyTo actor
   * @param timeout      amount of time to wait for states to match (default: 60 sec)
   * @param matcher      matcher to use (default: equality)
   */
  private def matchDemandStates(demandStates: Seq[DemandState], hcds: Set[ActorRef], replyTo: Option[ActorRef], runId: RunId,
                                timeout: Timeout = Timeout(60.seconds),
                                matcher: Matcher = StateVariable.defaultMatcher): Unit = {
    // Cancel any previous state matching, so that no timeout errors are sent to the replyTo actor
    stateMatcherActor.foreach(context.stop)
    replyTo.foreach { actorRef =>
      // Wait for the demand states to match the current states, then reply to the sender with the command status
      val props = HcdStatusMatcherActor.props(demandStates.toList, hcds, actorRef, runId, timeout, matcher)
      stateMatcherActor = Some(context.actorOf(props))
    }
  }

  /**
   * Returns a set of ActorRefs for the components that are resolved and match the config's prefix
   */
  private def getActorRefs(targetPrefix: String): Set[ActorRef] = {
    val x = locations.collect {
      case ResolvedAkkaLocation(_, _, prefix, actorRefOpt) if prefix == targetPrefix => actorRefOpt
    }
    x.flatten
  }

  /**
   * This method can be called from the setup method of an assembly to distribute parts of the configs to HDCs based on the
   * prefix. If the prefix of a SetupConfig matches the one for the HCD, it is sent to that HCD.
   *
   * @param configArg contains one or more configs
   * @param replyTo   send the command status (Completed) to this actor when all the configs are "matched" or an error status if a timeout occurs
   * @return Valid if locationsResolved, otherwise Invalid
   */
  def distributeSetupConfigs(configArg: SetupConfigArg, replyTo: Option[ActorRef] = None): Validation = {
    val pairs = for {
      config <- configArg.configs
      actorRef <- getActorRefs(config.prefix)
    } yield {
      actorRef ! HcdController.Submit(config)
      (actorRef, DemandState(config))
    }
    val hcds = pairs.map(_._1).toSet
    val demandStates = pairs.map(_._2)
    matchDemandStates(demandStates, hcds, replyTo, configArg.info.runId)
    Valid
  }
}
