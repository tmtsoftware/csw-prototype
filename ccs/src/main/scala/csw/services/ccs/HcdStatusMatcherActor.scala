package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import csw.util.akka.PublisherActor
import csw.util.cfg.StateVariable
import csw.util.cfg.StateVariable.{CurrentState, DemandState, Matcher}
import csw.util.cfg.RunId

import scala.concurrent.duration._

object HcdStatusMatcherActor {

  /**
   * Props used to create the HcdStatusMatcherActor actor.
   *
   * @param demands the target states that will be compared to their current states
   * @param hcds    the target HCD actors
   * @param replyTo the actor to reply to
   * @param runId   the runId to use in the reply
   * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
   * @param matcher the function used to compare the demand and current states
   */
  def props(demands: List[DemandState], hcds: Set[ActorRef], replyTo: ActorRef, runId: RunId = RunId(),
            timeout: Timeout = Timeout(60.seconds),
            matcher: Matcher = StateVariable.defaultMatcher): Props =
    Props(classOf[HcdStatusMatcherActor], demands, hcds, replyTo, runId, timeout, matcher)
}

/**
 * Subscribes to the current state values of a set of HCDs and notifies the
 * replyTo actor with the command status when they all match the respective demand states,
 * or with an error status message if the given timeout expires.
 *
 * See props for a description of the arguments.
 */
class HcdStatusMatcherActor(demands: List[DemandState], hcds: Set[ActorRef], replyTo: ActorRef, runId: RunId = RunId(),
                            timeout: Timeout = Timeout(60.seconds), matcher: Matcher = StateVariable.defaultMatcher) extends Actor with ActorLogging {

  import context.dispatcher

  context.become(waiting(Set[CurrentState]()))

  hcds.foreach(_ ! PublisherActor.Subscribe)
  val timer = context.system.scheduler.scheduleOnce(timeout.duration, self, timeout)

  override def receive: Receive = Actor.emptyBehavior

  // Waiting for all variables to match, which is the case when the results set contains
  // a matching current state for each demand state
  def waiting(results: Set[CurrentState]): Receive = {
    case current: CurrentState ⇒
      log.info(s"received current state: $current")
      demands.find(_.prefix == current.prefix).foreach { demand ⇒
        if (matcher(demand, current)) {
          val set = results + current
          if (set.size == demands.size) {
            timer.cancel()
            replyTo ! CommandStatus.Completed(runId)
            hcds.foreach(_ ! PublisherActor.Unsubscribe)
            context.stop(self)
          } else context.become(waiting(set))
        }
      }

    case `timeout` ⇒
      log.info(s"received timeout")
      replyTo ! CommandStatus.Error(runId, "Command timed out")
      hcds.foreach(_ ! PublisherActor.Unsubscribe)
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message $x")
  }
}
