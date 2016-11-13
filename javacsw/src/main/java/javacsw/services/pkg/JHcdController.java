package javacsw.services.pkg;

import csw.util.config.Configurations;

/**
 * Supports Java subclasses of HcdController and LifecycleHandler
 */
@SuppressWarnings("unused")
public abstract class JHcdController extends AbstractHcdController {
  @Override
  public void requestCurrent() {
  }

  @Override
  public abstract void process(Configurations.SetupConfig config);

  public JHcdController(HcdInfo info) {
    super(info);
  }
}
