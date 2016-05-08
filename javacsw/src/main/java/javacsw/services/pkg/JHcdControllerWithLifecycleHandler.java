package javacsw.services.pkg;

import csw.util.cfg.Configurations;

/**
 * Supports Java subclasses of HcdController and LifecycleHandler
 */
@SuppressWarnings("unused")
public abstract class JHcdControllerWithLifecycleHandler extends AbstractHcdControllerWithLifecycleHandler {
    @Override
    public abstract void requestCurrent();

    @Override
    public abstract void process(Configurations.SetupConfig config);
}
