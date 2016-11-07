package javacsw.services.ccs;

import csw.util.config.StateVariable;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Parent class of assembly controllers implemented in Java.
 * <p>
 * Note: The non-static methods here are only defined as public due to interoperability issues between Scala and Java
 * and should normally be protected (Actors only react to messages).
 *
 * Note: You probably want to use this class instead: {@link javacsw.services.pkg.JAssemblyControllerWithLifecycleHandler}
 *
 */
@SuppressWarnings("unused")
abstract public class JAssemblyController extends AbstractAssemblyController {

    /**
     * A request to the implementing actor to publish the current state value
     * by calling notifySubscribers().
     */
    @Override
    public /*protected*/ void requestCurrent() {}

    /**
     * This should be used by the implementer actor's receive method.
     * For example: def receive: Receive = controllerReceive orElse ...
     */
    @Override
    public /*protected*/ PartialFunction<Object, BoxedUnit> controllerReceive() {
        return super.controllerReceive();
    }

    /**
     * Notifies all subscribers with the given value (Need to override to keep java happy)
     */
    @Override
    public /*protected*/ void notifySubscribers(StateVariable.CurrentStates a) {
        super.notifySubscribers(a);
    }

}
