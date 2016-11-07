package javacsw.services.pkg;

import akka.actor.ActorRef;
import csw.services.ccs.Validation;
import csw.util.config.Configurations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyControllerWithLifecycleHandler extends AbstractAssemblyControllerWithLifecycleHandler {
  @Override
  public List<Validation.Validation> setup(Configurations.SetupConfigArg configArg, Optional<ActorRef> replyTo) {
    return Collections.emptyList();
  }

  @Override
  public List<Validation.Validation> observe(Configurations.ObserveConfigArg configArg, Optional<ActorRef> replyTo) {
    return Collections.emptyList();
  }

  public JAssemblyControllerWithLifecycleHandler(AssemblyInfo info) {
    super(info);
  }
}
