package javacsw.services.ccs;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.util.Timeout;
import csw.util.itemSet.RunId;
import csw.util.itemSet.StateVariable;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A factory for creating actors to wait for given HCD status messages.
 */
@SuppressWarnings("unused")
public class JHcdStatusMatcherActorFactory {
    /**
     * Returns a new HcdStatusMatcherActor actor that subscribes to the current state values of a set of HCDs and notifies the
     * replyTo actor with the command status when they all match the respective demand states,
     * or with an error status message if the timeout expires.
     *
     * @param f       the actor system or context to use to create the actor
     * @param demands the target states that will be compared to their current states
     * @param hcds    the target HCD actors
     * @param replyTo the actor to reply to
     * @return actorRef for the actor
     */
    public static ActorRef getHcdStatusMatcherActor(ActorRefFactory f, List<StateVariable.DemandState> demands, Set<ActorRef> hcds, ActorRef replyTo) {
        return f.actorOf(JHcdStatusMatcherActor.props(demands, hcds, replyTo));
    }

    /**
     * Returns a new HcdStatusMatcherActor actor that subscribes to the current state values of a set of HCDs and notifies the
     * replyTo actor with the command status when they all match the respective demand states,
     * or with an error status message if the timeout expires.
     *
     * @param f       the actor system or context to use to create the actor
     * @param demands the target states that will be compared to their current states
     * @param hcds    the target HCD actors
     * @param replyTo the actor to reply to
     * @param runId   the runId to use in the reply
     * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
     * @param matcher the function used to compare the demand and current states
     * @return actorRef for the actor
     */
    public static ActorRef getHcdStatusMatcherActor(ActorRefFactory f, List<StateVariable.DemandState> demands, Set<ActorRef> hcds, ActorRef replyTo, RunId runId,
                                                    Timeout timeout, BiFunction<StateVariable.DemandState, StateVariable.CurrentState, Boolean> matcher) {
        // XXX might need to be scala.Boolean!
        return f.actorOf(JHcdStatusMatcherActor.props(demands, hcds, replyTo, runId, timeout, matcher));
    }
}
