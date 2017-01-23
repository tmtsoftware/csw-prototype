package csw.examples.vsliceJava;

import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.examples.vsliceJava.assembly.TromboneAssembly;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import javacsw.services.alarms.IAlarmService;
import javacsw.services.alarms.IAlarmServiceAdmin;
import javacsw.services.alarms.JAlarmService;
import javacsw.services.alarms.JAlarmServiceAdmin;
import javacsw.services.cs.akka.JConfigServiceClient;
import javacsw.services.events.IEventService;
import javacsw.services.events.IEventServiceAdmin;
import javacsw.services.events.JEventService;
import javacsw.services.events.JEventServiceAdmin;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

  // Reset all redis based services before a test (assumes they are sharing the same Redis instance)
  public static void resetRedisServices(ActorSystem system) throws Exception {
    int t = 10;
    TimeUnit u = TimeUnit.SECONDS;
    Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(t, u));
    // clear redis
    IEventService eventService = IEventService.getEventService(IEventService.defaultName, system, timeout).get(t, u);
    IEventServiceAdmin eventServiceAdmin = new JEventServiceAdmin(eventService, system);
    eventServiceAdmin.reset().get(t, u);

    // initialize the list of alarms
    IAlarmService alarmService = IAlarmService.getAlarmService(system, timeout).get(t, u);
    IAlarmServiceAdmin alarmServiceAdmin = new JAlarmServiceAdmin(alarmService, system);
    //$CSW_INSTALL/conf/alarms.conf
    String cswInstall = System.getenv("CSW_INSTALL");
    if (cswInstall == null) {
      System.out.println("Environment variable CSW_INSTALL is not set");
      System.exit(1);
    }
    alarmServiceAdmin.initAlarms(new File(cswInstall + "/conf/alarms.conf"), false);
  }
}
