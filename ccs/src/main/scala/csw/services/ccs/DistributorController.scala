package csw.services.ccs

import akka.actor.{ ActorRef, Props, Actor, ActorLogging }
import akka.util.Timeout
import csw.services.ccs.StateMatcherActor.{ MatchingTimeout, StatesMatched }
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.{Disconnected, ServicesReady, ResolvedService}
import csw.services.loc.ServiceRef
import csw.shared.cmd.CommandStatus
import csw.util.config.Configurations.SetupConfigArg
import csw.util.config.StateVariable
import csw.util.config.StateVariable._
import scala.concurrent.duration._

/**
 * An assembly controller that forwards configs to HCDs or other assemblies based on
 * the service's prefix (as registered with the location service)
 */
object DistributorController {
  /**
   * Used to create the DistributorController actor
   * @param matcher Matcher to use when matching demand and current states (default checks for equality)
   * @param timeout Timeout when matching demand and current state variables(default: 10 secs)
   * @return
   */
  def props(matcher: Matcher = StateVariable.defaultMatcher, timeout: Timeout = Timeout(10.seconds)): Props =
    Props(classOf[DistributorController], matcher, timeout)

}

case class DistributorController(matcher: Matcher, timeout: Timeout) extends Actor with ActorLogging {

  private var services = Map[ServiceRef, ResolvedService]()

  private def process(configArg: SetupConfigArg): Unit = {
    context.actorOf(DistributorWorker.props(configArg, services, sender(), timeout, matcher))
  }

  private def servicesReady(services: Map[ServiceRef, ResolvedService]): Unit = {
    this.services = services
  }

  private def disconnected(): Unit = {
    this.services = Map[ServiceRef, ResolvedService]()
  }

  override def receive: Receive = {

    case config: SetupConfigArg ⇒ process(config)

    case ServicesReady(map)     ⇒ servicesReady(map)

    case Disconnected           ⇒ disconnected()
  }
}

/**
 * Worker that distributes the configs based on prefix and then waits for them to complete.
 */
object DistributorWorker {
  def props(configArg: SetupConfigArg, services: Map[ServiceRef, ResolvedService], replyTo: ActorRef,
            timeout: Timeout, matcher: Matcher): Props =
    Props(classOf[DistributorWorker], configArg, services, replyTo, timeout, matcher)
}

class DistributorWorker(configArg: SetupConfigArg, services: Map[ServiceRef, ResolvedService],
                        replyTo: ActorRef, timeout: Timeout, matcher: Matcher)
    extends Actor with ActorLogging {

  val demandStates = for {
    config ← configArg.configs
    service ← services.values.find(v ⇒ v.prefix == config.configKey.prefix && v.serviceRef.accessType == AkkaType)
    actorRef ← service.actorRefOpt
  } yield {
    val demand = DemandState(config.configKey.prefix, config.data)
    actorRef ! demand
    demand
  }
  context.actorOf(StateMatcherActor.props(demandStates.toList, self, timeout, matcher))

  override def receive: Receive = {
    case StatesMatched(states) ⇒ replyTo ! CommandStatus.Completed(configArg.info.runId)
    case MatchingTimeout       ⇒ replyTo ! CommandStatus.Error(configArg.info.runId, "Command timed out")
  }
}
