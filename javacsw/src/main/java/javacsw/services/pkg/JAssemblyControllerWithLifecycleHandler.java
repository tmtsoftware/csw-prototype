package javacsw.services.pkg;

import akka.actor.ActorRef;
import csw.services.ccs.AssemblyController.Validation;
import javacsw.util.cfg.JObserveConfigArg;
import javacsw.util.cfg.JSetupConfigArg;

import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyControllerWithLifecycleHandler extends AbstractAssemblyControllerWithLifecycleHandler {

    @Override
    public abstract Validation setup(Boolean locationsResolved, JSetupConfigArg configArg, Optional<ActorRef> replyTo);

    @Override
    public abstract Validation observe(Boolean locationsResolved, JObserveConfigArg configArg, Optional<ActorRef> replyTo);
}
