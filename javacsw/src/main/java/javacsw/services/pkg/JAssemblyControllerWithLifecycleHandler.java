package javacsw.services.pkg;

import akka.actor.ActorRef;
import csw.util.cfg.Configurations;
import csw.services.ccs.AssemblyController.Validation;

import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyControllerWithLifecycleHandler extends AbstractAssemblyControllerWithLifecycleHandler {

    @Override
    public abstract Validation setup(Boolean locationsResolved, Configurations.SetupConfigArg configArg, Optional<ActorRef> replyTo);

    @Override
    public abstract Validation observe(Boolean locationsResolved, Configurations.ObserveConfigArg configArg, Optional<ActorRef> replyTo);
}
