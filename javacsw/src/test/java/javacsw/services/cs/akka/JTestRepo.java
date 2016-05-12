package javacsw.services.cs.akka;


import akka.actor.ActorSystem;
import csw.services.cs.akka.ConfigServiceSettings;
import javacsw.services.cs.JBlockingConfigManager;
import javacsw.services.cs.JConfigManager;

/**
 * Utility class to create temporary Git or Svn repositories for use in testing.
 */
public class JTestRepo {
    /**
     * Gets a temporary svn or git repo for testing and returns a blocking config manager
     */
    public static JBlockingConfigManager getTestRepoBlockingConfigManager() {
        ActorSystem system = ActorSystem.create();
        ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
        if (settings.useSvn()) {
            return JTestSvnRepo.getJBlockingConfigManager();
        } else {
            return JTestGitRepo.getJBlockingConfigManager();
        }
    }

    /**
     * Gets a temporary svn or git repo for testing and returns the config manager
     */
    public static JConfigManager getTestRepoConfigManager() {
        ActorSystem system = ActorSystem.create();
        ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
        if (settings.useSvn()) {
            return JTestSvnRepo.getJConfigManager();
        } else {
            return JTestGitRepo.getJConfigManager();
        }
    }
}
