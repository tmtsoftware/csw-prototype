package javacsw.services.cs.akka.tests;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.cs.akka.ConfigServiceSettings;
import csw.services.cs.core.git.GitConfigManager;
import javacsw.services.cs.JBlockingConfigManager;
import javacsw.services.cs.JConfigManager;
import javacsw.services.cs.core.JBlockingConfigManagerImpl;
import javacsw.services.cs.core.JConfigManagerImpl;

import java.io.File;
import java.util.Objects;

/**
 * Java utility class to create a temporary Git repository for use in testing.
 */
public class JTestGitRepo {
    private static void resetRepo(ConfigServiceSettings settings, ActorRefFactory context) {
        // XXX FIXME TODO: Use generated temp dirs, not settings
        System.out.println("Local repo = " + settings.localRepository() + ", remote = " + settings.mainRepository());
        if (!Objects.equals(settings.mainRepository().getScheme(), "file"))
            throw new RuntimeException("Please specify a file URI for csw.services.cs.main-repository for testing");

        File gitMainRepo = new File(settings.mainRepository().getPath());
        // Delete the main and local test repositories (Only use this in test cases!)
        GitConfigManager.deleteDirectoryRecursively(gitMainRepo);
        GitConfigManager.initBareRepo(gitMainRepo, context);
        GitConfigManager.deleteDirectoryRecursively(settings.localRepository());
    }


    /**
     * Java API: Creates a temporary test Git repository.
     * Any previous contents are deleted.
     *
     * @return a new blocking ConfigManager set to manage the newly created Git repository
     */
    public static JBlockingConfigManager getJBlockingConfigManager(ActorSystem system) {
        ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
        resetRepo(settings, system);
        GitConfigManager manager = GitConfigManager.apply(settings.localRepository(), settings.mainRepository(), settings.name(), system);
        return new JBlockingConfigManagerImpl(manager, system);
    }

    /**
     * Java API: Creates a temporary test Git repository.
     * Any previous contents are deleted.
     *
     * @return a new ConfigManager set to manage the newly created Git repository
     */
    public static JConfigManager getJConfigManager(ActorSystem system) {
        ConfigServiceSettings settings = ConfigServiceSettings.getConfigServiceSettings(system);
        resetRepo(settings, system);
        GitConfigManager manager = GitConfigManager.apply(settings.localRepository(), settings.mainRepository(), settings.name(), system);
        return new JConfigManagerImpl(manager, system);
    }
}
