package javacsw.services.ccs;

import csw.util.cfg.Configurations;
import csw.util.cfg.StateVariable;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * Parent class of HCD controllers implemented in Java
 */
abstract public class JHcdController extends AbstractHcdController {
    @Override
    abstract public void process(Configurations.SetupConfig config);

    @Override
    abstract public void requestCurrent();

    @Override
    public PartialFunction<Object, BoxedUnit> controllerReceive() {
        return super.controllerReceive();
    }

    @Override
    public void notifySubscribers(StateVariable.CurrentState a) {
        super.notifySubscribers(a);
    }
}
