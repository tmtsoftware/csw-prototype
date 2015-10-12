package csw.services.ccs

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import csw.services.kvs.Subscriber
import csw.util.config.StateVariable
import csw.util.config.StateVariable._
import scala.concurrent.duration._

// Needed for Subscriber below
import csw.services.kvs.Implicits.currentStateKvsFormatter

object StateMatcherActor {

  /**
   * Base trait of reply messages
   */
  sealed trait StateMatcherReply

  /**
   * Reply when all states match
   * @param states the current states
   */
  case class StatesMatched(states: Set[CurrentState]) extends StateMatcherReply

  /**
   * Reply when matching timed out
   */
  case object MatchingTimeout extends StateMatcherReply

  /**
   * Props used to create the actor.
   *
   * @param demands the target states that will be compared to their current states
   * @param replyTo the actor to reply to
   * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
   * @param matcher the function used to compare the demand and current states
   */
  def props(demands: List[DemandState], replyTo: ActorRef,
            timeout: Timeout = Timeout(10.seconds),
            matcher: Matcher = StateVariable.defaultMatcher): Props =
    Props(classOf[StateMatcherActor], demands, replyTo, timeout, matcher)
}

/**
 * Subscribes to the current values for the given demand values and notifies the
 * replyTo actor with the current states when they all match the
 * respective demand states, or with a timeout message if the given timeout expires.
 *
 * See props for a description of the arguments.
 */
class StateMatcherActor(demands: List[DemandState], replyTo: ActorRef, timeout: Timeout, matcher: Matcher)
    extends Subscriber[CurrentState] {

  import csw.services.ccs.StateMatcherActor._
  import context.dispatcher
  context.become(waiting(Set[CurrentState]()))
  val keys = demands.map(CurrentState.makeExtKey)
  log.info(s"Subscribing to ${keys.mkString(", ")}")
  subscribe(keys: _*)
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
            replyTo ! StatesMatched(set)
            context.stop(self)
          } else context.become(waiting(set))
        }
      }

    case `timeout` ⇒
      log.info(s"received timeout")
      replyTo ! MatchingTimeout
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message $x")
  }
}
