package csw.services.ccs

import akka.actor.{Props, ActorRef}
import akka.util.Timeout
import csw.services.kvs.Subscriber
import csw.util.config.StateVariable
import csw.util.config.StateVariable._
import scala.concurrent.duration._

// Needed for Subscriber below
import csw.services.kvs.Implicits.currentStateByteStringFormatter

object StateMatcherActor {
  /**
   * Props used to create the actor.
   *
   * @param demand the target state that will be compared to the current state
   * @param replyTo the actor to reply to
   * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
   * @param matcher the function used to compare the demand and current states
   */
  def props(demand: DemandState, replyTo: ActorRef,
            timeout: Timeout = Timeout(10.seconds),
            matcher: Matcher = StateVariable.defaultMatcher): Props =
    Props(classOf[StateMatcherActor], demand, replyTo, timeout, matcher)
}

/**
 * Subscribes to the current value and notifies the replyTo actor with the current state when it matches the
 * demand state, or with a timeout message when it times out.
 *
 * See props for a description of the arguments.
 */
class StateMatcherActor(demand: DemandState, replyTo: ActorRef, timeout: Timeout, matcher: Matcher)
  extends Subscriber[CurrentState] {

  import context.dispatcher

  val key = CurrentState.makeExtKey(demand)
  log.info(s"Subscribing to $key")
  subscribe(key)
  val timer = context.system.scheduler.scheduleOnce(timeout.duration, self, timeout)

  def receive: Receive = {
    case current: CurrentState =>
      log.info(s"received current state: $current")
      if (matcher(demand, current)) {
        timer.cancel()
        replyTo ! current
        context.stop(self)
      }

    case `timeout` =>
      log.info(s"received timeout")
      replyTo ! timeout
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message $x")
  }
}
