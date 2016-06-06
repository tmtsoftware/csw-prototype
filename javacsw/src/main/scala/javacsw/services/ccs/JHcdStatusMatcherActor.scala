package javacsw.services.ccs

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import csw.services.ccs.HcdStatusMatcherActor
import csw.util.config.StateVariable._
import csw.util.config.{RunId, StateVariable}
import scala.concurrent.duration._

object JHcdStatusMatcherActor {

  /**
   * Props used to create the HcdStatusMatcherActor actor from Java.
   *
   * @param demands the target states that will be compared to their current states
   * @param hcds    the target HCD actors
   * @param replyTo the actor to reply to
   */
  def props(demands: java.util.List[DemandState], hcds: java.util.Set[ActorRef], replyTo: ActorRef): Props = {
    import scala.collection.JavaConversions._
    Props(classOf[HcdStatusMatcherActor], demands.toList, hcds.toSet, replyTo, RunId(), Timeout(60.seconds),
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
    import scala.collection.JavaConversions._
    import scala.compat.java8.FunctionConverters._
    Props(classOf[HcdStatusMatcherActor], demands.toList, hcds.toSet, replyTo, runId, timeout, matcher.asScala)
  }
}
