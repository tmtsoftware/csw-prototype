package javacsw.services.cs.akka;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.cs.akka.ConfigServiceSettings;
import csw.services.cs.core.svn.SvnConfigManager;
import javacsw.services.cs.JBlockingConfigManager;
import javacsw.services.cs.JConfigManager;
import javacsw.services.cs.core.JBlockingConfigManagerImpl;
import javacsw.services.cs.core.JConfigManagerImpl;

import java.io.File;
import java.util.Objects;

/**
 * Java utility class to create a temporary Svn repository for use in testing.
 */
public class JTestSvnRepo {

    private static void resetRepo(ConfigServiceSettings settings, ActorRefFactory context) {
        // XXX FIXME TODO: Use generated temp dirs, not settings
        System.out.println("Using test svn repo at = " + settings.mainRepository());
        if (!Objects.equals(settings.mainRepository().getScheme(), "file"))
            throw new RuntimeException("Please specify a file URI for csw.services.cs.main-repository for testing");

        File svnMainRepo = new File(settings.mainRepository().getPath());
        // Delete the main and local test repositories (Only use this in test cases!)
        SvnConfigManager.deleteDirectoryRecursively(svnMainRepo);
        SvnConfigManager.initSvnRepo(svnMainRepo, context);
    }

  /**
   * Java API: Creates a temporary test Svn repository.
   * Any previous contents are deleted.
   *
   * @return a new blocking ConfigManager set to manage the newly created Svn repository
   */
  public static JBlockingConfigManager getJBlockingConfigManager(ActorSystem system) {
      ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
      resetRepo(settings, system);
      SvnConfigManager manager = SvnConfigManager.apply(settings.mainRepository(), settings.name(), system);
      return new JBlockingConfigManagerImpl(manager, system);
  }

  /**
    * Java API: Creates a temporary test Svn repository.
    * Any previous contents are deleted.
    *
    * @return a new ConfigManager set to manage the newly created Svn repository
    */
  public static JConfigManager getJConfigManager(ActorSystem system) {
      ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
      resetRepo(settings, system);
      SvnConfigManager manager = SvnConfigManager.apply(settings.mainRepository(), settings.name(), system);
      return new JConfigManagerImpl(manager, system);
  }
}
