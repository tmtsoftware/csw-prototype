package javacsw.services.ccs;

import akka.actor.ActorRef;
import csw.services.ccs.Validation;
import csw.util.param.Parameters.Setup;
import csw.util.param.Parameters.Observe;
import javacsw.services.pkg.AbstractAssemblyControllerWithPubSub;

import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyControllerWithPubSub extends AbstractAssemblyControllerWithPubSub {
    @Override
    public abstract Validation.Validation setup(Setup configArg, Optional<ActorRef> replyTo);

    @Override
    public abstract Validation.Validation observe(Observe configArg, Optional<ActorRef> replyTo);

    public JAssemblyControllerWithPubSub(AssemblyInfo info) {
        super(info);
    }

    /**
     * This should be used by the implementer actor's receive method.
     * For example: return jDefaultReceive().orElse(...)
     */
    protected Receive jDefaultReceive() {
        return new Receive(super.defaultReceive());
    }
}
