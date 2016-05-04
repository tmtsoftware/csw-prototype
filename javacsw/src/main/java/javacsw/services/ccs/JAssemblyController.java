package javacsw.services.ccs;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import csw.services.ccs.AssemblyController;
import csw.services.loc.Connection;
import csw.services.loc.LocationService;
import csw.services.loc.LocationService.*;
import csw.util.cfg.Configurations;
import csw.util.cfg.RunId;
import csw.util.cfg.StateVariable;
import csw.util.cfg.StateVariable.*;
import scala.Function2;
import scala.Option;
import scala.PartialFunction;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;
import akka.util.Timeout;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.*;

/**
 * Parent class of assembly controllers implemented in Java.
 * <p>
 * Note: The non-static methods here are only defined as public due to interoperability issues between Scala and Java
 * and should normally be protected (Actors only react to messages).
 */
abstract public class JAssemblyController extends AbstractAssemblyController {


    /**
     * A request to the implementing actor to publish the current state value
     * by calling notifySubscribers().
     */
    @Override
    public /*protected*/ abstract void requestCurrent();

    /**
     * This should be used by the implementer actor's receive method.
     * For example: def receive: Receive = controllerReceive orElse ...
     */
    @Override
    public /*protected*/ PartialFunction<Object, BoxedUnit> controllerReceive() {
        return super.controllerReceive();
    }

    @Override
    /**
     * Notifies all subscribers with the given value (Need to override to keep java happy)
     */
    public /*protected*/ void notifySubscribers(StateVariable.CurrentState a) {
        super.notifySubscribers(a);
    }

}
