package javacsw.services.ccs;

import akka.actor.ActorRef;
import csw.services.ccs.Validation;
import csw.util.param.Parameters.Setup;
import csw.util.param.Parameters.Observe;
import javacsw.services.pkg.AbstractAssemblyController;

import java.util.Optional;

/**
 * Supports Java subclasses of AssemblyController
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public abstract class JAssemblyController extends AbstractAssemblyController {
  @Override
  public abstract Validation.Validation setup(Setup s, Optional<ActorRef> replyTo);

  @Override
  public abstract Validation.Validation observe(Observe o, Optional<ActorRef> replyTo);

  public JAssemblyController(AssemblyInfo info) {
    super(info);
  }

  /**
   * This should be used by the implementer actor's receive method.
   * For example: return jControllerReceive().orElse(...)
   */
  protected Receive jControllerReceive() {
    return new Receive(super.controllerReceive());
  }
}
