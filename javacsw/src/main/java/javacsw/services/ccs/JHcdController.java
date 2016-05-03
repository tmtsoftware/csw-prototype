package javacsw.services.ccs;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import csw.util.cfg.Configurations;
import csw.util.cfg.RunId;
import csw.util.cfg.StateVariable;
import csw.util.cfg.StateVariable.*;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import akka.util.Timeout;

import java.util.List;
import java.util.Set;
import java.util.function.*;

/**
 * Parent class of HCD controllers implemented in Java
 */
abstract public class JHcdController extends AbstractHcdController {

    /**
     * Returns a new HcdStatusMatcherActor actor that subscribes to the current state values of a set of HCDs and notifies the
     * replyTo actor with the command status when they all match the respective demand states,
     * or with an error status message if the timeout expires.
     *
     * @param f the actor system or context to use to create the actor
     * @param demands the target states that will be compared to their current states
     * @param hcds the target HCD actors
     * @param replyTo the actor to reply to
     */
    static ActorRef getHcdStatusMatcherActor(ActorRefFactory f, List<DemandState> demands, Set<ActorRef> hcds, ActorRef replyTo) {
        return f.actorOf(JHcdStatusMatcherActor.props(demands, hcds, replyTo));
    }

    /**
     * Returns a new HcdStatusMatcherActor actor that subscribes to the current state values of a set of HCDs and notifies the
     * replyTo actor with the command status when they all match the respective demand states,
     * or with an error status message if the timeout expires.
     *
     * @param f the actor system or context to use to create the actor
     * @param demands the target states that will be compared to their current states
     * @param hcds the target HCD actors
     * @param replyTo the actor to reply to
     * @param runId   the runId to use in the reply
     * @param timeout the amount of time to wait for a match before giving up and replying with a Timeout message
     * @param matcher the function used to compare the demand and current states
     */
    static ActorRef getHcdStatusMatcherActor(ActorRefFactory f, List<DemandState> demands, Set<ActorRef> hcds, ActorRef replyTo, RunId runId,
                                      Timeout timeout, BiFunction<DemandState, CurrentState, java.lang.Boolean>  matcher) {
        return f.actorOf(JHcdStatusMatcherActor.props(demands, hcds, replyTo, runId, timeout, matcher));
    }

    /**
     * Derived classes should process the given config and eventually either call
     * notifySubscribers() or send a CurrentState message to itself
     * (possibly from a worker actor) to indicate changes in the current HCD state.
     */
    @Override
    abstract public void process(Configurations.SetupConfig config);

    /**
     * A request to the implementing actor to publish the current state value
     * by calling notifySubscribers().
     */
    @Override
    abstract public void requestCurrent();

    /**
     * This should be used by the implementer actor's receive method.
     * For example: def receive: Receive = controllerReceive orElse ...
     */
    @Override
    public PartialFunction<Object, BoxedUnit> controllerReceive() {
        return super.controllerReceive();
    }

    @Override
    /**
     * Notifies all subscribers with the given value (Need to override to keep java happy)
     */
    public void notifySubscribers(StateVariable.CurrentState a) {
        super.notifySubscribers(a);
    }
}
