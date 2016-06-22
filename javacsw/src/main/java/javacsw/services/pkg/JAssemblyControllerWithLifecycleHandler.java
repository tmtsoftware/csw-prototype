package javacsw.services.pkg;

import akka.actor.ActorRef;
import csw.services.ccs.AssemblyController;
import csw.util.config.Configurations;
import csw.util.config.Configurations.*;
import scala.concurrent.Future;

import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyControllerWithLifecycleHandler extends AbstractAssemblyControllerWithLifecycleHandler {

    @Override
    public abstract Validation setup(Boolean locationsResolved, SetupConfigArg configArg, Optional<ActorRef> replyTo);

    @Override
    public abstract Validation observe(Boolean locationsResolved, ObserveConfigArg configArg, Optional<ActorRef> replyTo);

    @Override
    public abstract Future<RequestResult> request(boolean locationsResolved, SetupConfig config);
}
