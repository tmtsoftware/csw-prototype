package javacsw.services.ccs;

import akka.actor.ActorRef;
import csw.services.ccs.Validation;
import csw.util.config.Configurations;
import javacsw.services.pkg.AbstractAssemblyController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyController extends AbstractAssemblyController {
  @Override
  public List<Validation.Validation> setup(Configurations.SetupConfigArg configArg, Optional<ActorRef> replyTo) {
    return Collections.emptyList();
  }

  @Override
  public List<Validation.Validation> observe(Configurations.ObserveConfigArg configArg, Optional<ActorRef> replyTo) {
    return Collections.emptyList();
  }

  public JAssemblyController(AssemblyInfo info) {
    super(info);
  }
}
