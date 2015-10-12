package csw.services.ccs

import akka.actor.{ ActorRef, Props, Actor, ActorLogging }
import akka.util.Timeout
import csw.services.ccs.StateMatcherActor.{ MatchingTimeout, StatesMatched }
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.ResolvedService
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
trait AssemblyDistributorController extends AssemblyController {

  private var services = Map[ServiceRef, ResolvedService]()

  /**
   * Timeout when matching demand and current state variables(default: 10 secs)
   */
  val timeout = Timeout(10.seconds)

  /**
   * Matcher to use when matching demand and current states (default checks for equality)
   */
  val matcher: Matcher = StateVariable.defaultMatcher

  override protected def process(configArg: SetupConfigArg): Unit = {
    context.actorOf(AssemblyDistributorWorker.props(configArg, services, sender(), timeout, matcher))
  }

  override protected def servicesReady(services: Map[ServiceRef, ResolvedService]): Unit = {
    this.services = services
  }

  override protected def disconnected(): Unit = {
    this.services = Map[ServiceRef, ResolvedService]()
  }
}

/**
 * Worker that distributes the configs based on prefix and then waits for them to complete.
 */
object AssemblyDistributorWorker {
  def props(configArg: SetupConfigArg, services: Map[ServiceRef, ResolvedService], replyTo: ActorRef,
            timeout: Timeout, matcher: Matcher): Props =
    Props(classOf[AssemblyDistributorWorker], configArg, services, replyTo, timeout, matcher)
}

class AssemblyDistributorWorker(configArg: SetupConfigArg, services: Map[ServiceRef, ResolvedService],
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
