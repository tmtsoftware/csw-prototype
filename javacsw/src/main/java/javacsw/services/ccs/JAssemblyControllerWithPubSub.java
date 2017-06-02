package javacsw.services.ccs;

import akka.actor.ActorRef;
import csw.services.ccs.Validation;
import csw.util.itemSet.ItemSets;
import javacsw.services.pkg.AbstractAssemblyControllerWithPubSub;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyControllerWithPubSub extends AbstractAssemblyControllerWithPubSub {
    @Override
    public List<Validation.Validation> setup(ItemSets.SetupConfigArg configArg, Optional<ActorRef> replyTo) {
        return Collections.emptyList();
    }

    @Override
    public List<Validation.Validation> observe(ItemSets.ObserveConfigArg configArg, Optional<ActorRef> replyTo) {
        return Collections.emptyList();
    }

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
