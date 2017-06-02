package javacsw.services.ccs

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import csw.services.ccs.HcdStatusMatcherActor
import csw.util.itemSet.StateVariable._
import csw.util.itemSet.{RunId, StateVariable}
import scala.concurrent.duration._

object JHcdStatusMatcherActor {

  /**
   * Props used to create the HcdStatusMatcherActor actor from Java.
   *
   * @param demand the target state that will be compared to the current state
   * @param hcd    the target HCD actor
   * @param replyTo the actor to reply to
   */
  def props(demand: DemandState, hcd: ActorRef, replyTo: ActorRef): Props = {
    Props(classOf[HcdStatusMatcherActor], List(demand), Set(hcd), replyTo, RunId(), Timeout(60.seconds),
      StateVariable.defaultMatcher _)
  }

  /**
   * Props used to create the HcdStatusMatcherActor actor from Java.
   *
   * @param demands the target states that will be compared to their current states
   * @param hcds    the target HCD actors
   * @param replyTo the actor to reply to
   */
  def props(demands: java.util.List[DemandState], hcds: java.util.Set[ActorRef], replyTo: ActorRef): Props = {
    import scala.collection.JavaConverters._
    Props(classOf[HcdStatusMatcherActor], demands.asScala.toList, hcds.asScala.toSet, replyTo, RunId(), Timeout(60.seconds),
      StateVariable.defaultMatcher _)
  }

  /**
   * Props used to create the HcdStatusMatcherActor actor from Java.
   *
   * @param demands the target states that will be compared to their current states
   * @param hcds    the target HCD actors
   * @param replyTo the actor to reply to
   * @param runId   the runId to use in the reply
   * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
   * @param matcher the function used to compare the demand and current states
   */
  def props(demands: java.util.List[DemandState], hcds: java.util.Set[ActorRef], replyTo: ActorRef, runId: RunId,
            timeout: Timeout,
            // XXX Might need to be scala.Boolean!
            matcher: java.util.function.BiFunction[DemandState, CurrentState, java.lang.Boolean]): Props = {
    import scala.collection.JavaConverters._
    import scala.compat.java8.FunctionConverters._
    Props(classOf[HcdStatusMatcherActor], demands.asScala.toList, hcds.asScala.toSet, replyTo, runId, timeout, matcher.asScala)
  }
}
