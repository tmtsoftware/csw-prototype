package csw.examples.vsliceJava;

import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.examples.vsliceJava.assembly.TromboneAssembly;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import javacsw.services.cs.akka.JConfigServiceClient;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for setting up the test environment
 */
@SuppressWarnings("WeakerAccess")
public class TestEnv {

  // For the tests, store the HCD's configuration in the config service (Normally, it would already be there)
  public static void createTromboneHcdConfig(ActorSystem system) throws ExecutionException, InterruptedException {
    Config config = ConfigFactory.parseResources(TromboneHCD.resource.getPath());
    Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
    JConfigServiceClient.saveConfigToConfigService(TromboneHCD.tromboneConfigFile, config, system, timeout).get();
  }

  // For the tests, store the assembly's configuration in the config service (Normally, it would already be there)
  public static void createTromboneAssemblyConfig(ActorSystem system) throws ExecutionException, InterruptedException {
    createTromboneHcdConfig(system);
    Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
    Config config = ConfigFactory.parseResources(TromboneAssembly.resource.getPath());
    JConfigServiceClient.saveConfigToConfigService(TromboneAssembly.tromboneConfigFile, config, system, timeout).get();
  }
}
