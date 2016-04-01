package csw.services.ccs

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import csw.services.kvs.{KvsSettings, StateVariableStore, Subscriber}
import csw.util.cfg.Configurations.StateVariable
import csw.util.cfg.Configurations.StateVariable.{CurrentState, DemandState, Matcher}
import csw.util.cfg.RunId

import scala.concurrent.duration._

object StateMatcherActor {

  /**
   * Props used to create the actor.
   *
   * @param demands the target states that will be compared to their current states
   * @param replyTo the actor to reply to
   * @param runId the runId to use in the reply
   * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
   * @param matcher the function used to compare the demand and current states
   */
  def props(demands: List[DemandState], replyTo: ActorRef, runId: RunId = RunId(),
            timeout: Timeout = Timeout(60.seconds),
            matcher: Matcher = StateVariable.defaultMatcher): Props =
    Props(classOf[StateMatcherActor], demands, replyTo, runId, timeout, matcher)
}

/**
 * Subscribes to the current values for the given demand values and notifies the
 * replyTo actor with the command status when they all match the respective demand states,
 * or with an error status message if the given timeout expires.
 *
 * See props for a description of the arguments.
 */
class StateMatcherActor(demands: List[DemandState], replyTo: ActorRef, runId: RunId,
                        timeout: Timeout, matcher: Matcher)
    extends Subscriber[CurrentState] {

  import context.dispatcher
  context.become(waiting(Set[CurrentState]()))
  val keys = demands.map(_.prefix)
  log.info(s"Subscribing to ${keys.mkString(", ")}")
  subscribe(keys: _*)

  // Subscribe only sends us a message if the value changes. We also need to
  // check if the value already matches the demand.
  val svs = StateVariableStore(KvsSettings(context.system))
  keys.foreach { k ⇒
    svs.get(k).onSuccess {
      case Some(v) ⇒ self ! v
      case None    ⇒
    }
  }

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
            svs.disconnect()
            context.stop(self)
          } else context.become(waiting(set))
        }
      }

    case `timeout` ⇒
      log.info(s"received timeout")
      replyTo ! CommandStatus.Error(runId, "Command timed out")
      svs.disconnect()
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message $x")
  }
}
