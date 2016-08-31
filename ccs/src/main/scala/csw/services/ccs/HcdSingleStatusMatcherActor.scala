package csw.services.ccs

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import csw.util.akka.PublisherActor
import csw.util.config.{RunId, StateVariable}
import csw.util.config.StateVariable.{CurrentState, DemandState, Matcher}
import scala.concurrent.duration._

/**
  * TMT Source Code: 8/27/16.
  */
class HcdSingleStatusMatcherActor(demand: DemandState, hcd: ActorRef, replyTo: ActorRef, runId: RunId,
                                  timeout: Timeout, matcher: Matcher) extends Actor with ActorLogging {
  import context.dispatcher

  context.become(waiting(Set.empty[CurrentState]))

  log.info("Subscribing")
  hcd ! PublisherActor.Subscribe
  val timer = context.system.scheduler.scheduleOnce(timeout.duration, self, timeout)

  def receive: Receive = Actor.emptyBehavior

  // Waiting for all variables to match, which is the case when the results set contains
  // a matching current state for each demand state
  def waiting(results: Set[CurrentState]): Receive = {
    case current: CurrentState =>
      log.info(s"received current state: $current")
      if (demand.prefix == current.prefix && matcher(demand, current)) {
        val set = results + current
        timer.cancel()
        replyTo ! CommandStatus.Completed(runId)
        hcd ! PublisherActor.Unsubscribe
        context.stop(self)
      } else context.become(waiting(results))

    case `timeout` =>
      log.debug(s"received timeout")
      replyTo ! CommandStatus.Error(runId, "Command timed out")
      hcd ! PublisherActor.Unsubscribe
      context.stop(self)

    case x => log.error(s"Unexpected message $x")
  }
}

object HcdSingleStatusMatcherActor {
  /**
    * Props used to create the HcdStatusMatcherActor actor.
    *
    * @param demand  the target state that will be compared to their current states
    * @param hcd    the target HCD actor
    * @param replyTo the actor to reply to
    * @param runId   the runId to use in the reply
    * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
    * @param matcher the function used to compare the demand and current states
    */
  def props(demand: DemandState, hcd: ActorRef, replyTo: ActorRef,
            runId:   RunId   = RunId(),
            timeout: Timeout = Timeout(60.seconds),
            matcher: Matcher = StateVariable.defaultMatcher): Props =
  Props(classOf[HcdSingleStatusMatcherActor], demand, hcd, replyTo, runId, timeout, matcher)
}