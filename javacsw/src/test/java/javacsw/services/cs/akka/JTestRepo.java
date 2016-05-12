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
    public static JBlockingConfigManager getTestRepoBlockingConfigManager(ActorSystem system) {
        ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
        if (settings.useSvn()) {
            return JTestSvnRepo.getJBlockingConfigManager(system);
        } else {
            return JTestGitRepo.getJBlockingConfigManager(system);
        }
    }

    /**
     * Gets a temporary svn or git repo for testing and returns the config manager
     */
    public static JConfigManager getTestRepoConfigManager(ActorSystem system) {
        ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
        if (settings.useSvn()) {
            return JTestSvnRepo.getJConfigManager(system);
        } else {
            return JTestGitRepo.getJConfigManager(system);
        }
    }
}
