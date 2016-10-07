package javacsw.services.alarms.tests;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.alarms.AlarmKey;
import csw.services.alarms.AscfValidation;
import javacsw.services.alarms.*;
import javacsw.services.alarms.IAlarmService.*;
import javacsw.services.alarms.JAlarmModel.*;
import javacsw.services.alarms.JAlarmState.*;
import csw.services.alarms.AlarmModel;
import csw.services.alarms.AlarmModel.*;
import csw.services.alarms.AlarmService.*;
import csw.services.loc.LocationService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Tests the Java API to the Alarm Service
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalGetWithoutIsPresent"})
public class JBlockingAlarmServiceTests {
  private static ActorSystem system;
  private static LoggingAdapter logger;
  private static IBlockingAlarmService alarmService;
  private static Timeout timeout = new Timeout(FiniteDuration.create(15, TimeUnit.SECONDS));

  // Set a low refresh rate for the test
  private static int refreshSecs = 1;

  // Time until alarm severity expires
  private static int expireSecs = refreshSecs * IBlockingAlarmService.maxMissedRefresh;

  // Time in ms to wait for a Redis callback
  private static int shortDelayMs = 500;

  // Time in ms to wait to see if an alarm severity expired
  private static int delayMs = expireSecs * 1000 + shortDelayMs;

  public JBlockingAlarmServiceTests() throws URISyntaxException {
  }

  @BeforeClass
  public static void setup() throws ExecutionException, InterruptedException {
    LocationService.initInterface();
    system = ActorSystem.create();

    String asName = "Blocking Alarm Service Test";

    // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
    // Start redis on a random port and register it with the location service.
    // The following is the equivalent of running this from the command line:
    //   tracklocation --name "Blocking Alarm Service Test" --command "redis-server --port %port" --no-exit
    IAlarmAdmin.startAlarmService(asName, true, system.dispatcher());

    // Later, in another JVM...,
    // Get the alarm service by looking up the name with the location service (using a small value for refreshSecs for testing)
    alarmService = IBlockingAlarmService.getAlarmService(asName, refreshSecs, system, timeout);
  }

  @AfterClass
  public static void teardown() {
    IBlockingAlarmAdmin admin = new JBlockingAlarmAdmin(alarmService, timeout, system);
    admin.shutdown();
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  // For testing callback
  private static CurrentSeverity callbackSev = new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected);
  private static Optional<Health> callbackHealth = Optional.empty();

  // Called when alarm severity changes
  static AlarmHandler alarmHandler = new AlarmHandler() {
    public void handleAlarmStatus(AlarmStatus alarmStatus) {
      AlarmKey a = alarmStatus.alarmKey();
      logger.debug("Alarm Status: " + a.subsystem() + ":" + a.component() + ":" + a.name() + ": " + alarmStatus.currentSeverity());
      callbackSev = alarmStatus.currentSeverity();
    }
  };

  // Called when health severity changes
  static HealthHandler healthHandler = new HealthHandler() {
    public void handleHealthStatus(HealthStatus healthStatus) {
      AlarmKey a = healthStatus.key();
      logger.debug("Health Status: " + a.subsystem() + ":" + a.component() + ":" + a.name() + ": " + healthStatus.health());
      callbackHealth = Optional.of(healthStatus.health());
    }
  };

  // Note: In the tests below we just call .get() on the future results for simplicity.
  // In a real application, you could use other methods...

  @Test
  public void basicJavaTests() throws Exception {
    logger = Logging.getLogger(system, this);
    // Get the test alarm service config file (ascf)
    URL url = JBlockingAlarmServiceTests.class.getResource("/test-alarms.conf");
    File ascf = Paths.get(url.toURI()).toFile();

    // initialize the list of alarms in Redis
    IBlockingAlarmAdmin admin = new JBlockingAlarmAdmin(alarmService, timeout, system);
    List<AscfValidation.Problem> problems = admin.initAlarms(ascf, false);
    JProblem.printProblems(problems);
    assertTrue(JProblem.errorCount(problems) == 0);

    // List all the alarms that were written to Redis
    List<AlarmModel> alarms = alarmService.getAlarms(JAlarmKey.create());
    for (AlarmModel alarm : alarms) {
      // XXX TODO: compare results
      logger.debug("List Alarm: " + alarm);
    }

    // Test working with an alarm and monitoring the alarm severity level
    AlarmKey key1 = new AlarmKey("TCS", "tcsPk", "cpuExceededAlarm");
    AlarmKey key2 = new AlarmKey("NFIRAOS", "envCtrl", "minTemperature");
    AlarmKey key3 = new AlarmKey("NFIRAOS", "envCtrl", "maxTemperature");

    AlarmMonitor alarmMonitor = alarmService.monitorAlarms(key1,
      Optional.empty(), Optional.of(alarmHandler), Optional.of(healthHandler), false);
    Thread.sleep(shortDelayMs); // make sure actor has started

    alarmService.setSeverity(key1, JSeverityLevel.Critical);
    Thread.sleep(delayMs); // wait for severity to expire

    // alarm is latched, so stays at critical
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Critical));
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Critical));

    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    // alarm is latched, so stays at critical
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Critical));
    Thread.sleep(shortDelayMs);
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Critical));

    // Acknowledge the alarm, which clears it, resets it back to Okay
    alarmService.acknowledgeAndResetAlarm(key1);
    alarmService.setSeverity(key1, JSeverityLevel.Okay);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback, so the test below passes
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Okay, JSeverityLevel.Okay)); // alarm was cleared
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Okay, JSeverityLevel.Okay));

    Thread.sleep(delayMs); // wait for severity to expire and become "Disconnected"
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected)); // alarm severity key expired
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected));

    // Test alarm in shelved state
    alarmService.setShelvedState(key1, JShelvedState.Shelved);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    // getSeverity should return the severity that was set ...
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    // but the callback should not have been called, since alarm is shelved (callbackSev should have previous value)
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected));
    // un-shelve the alarm and try it again
    alarmService.setShelvedState(key1, JShelvedState.Normal);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    // Since the alarm is no longer shelved, the callback should be called this time
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));

    // Test alarm in deactivated state
    alarmService.acknowledgeAndResetAlarm(key1);
    alarmService.setSeverity(key1, JSeverityLevel.Okay);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    alarmService.setActivationState(key1, JActivationState.OutOfService);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    // callback should not have been called, callbackSev should have previous value
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Okay, JSeverityLevel.Okay));
    // reactivate the alarm
    alarmService.setActivationState(key1, JActivationState.Normal);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    // This time the callback should have been called
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    assertEquals(alarmService.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));

    // Test health monitor
    alarmMonitor.stop();
    AlarmKey nfKey = JAlarmKey.create(Optional.of("NFIRAOS"));
    AlarmMonitor healthMonitor = alarmService.monitorAlarms(nfKey,
      Optional.empty(), Optional.of(alarmHandler), Optional.of(healthHandler), false);
    Thread.sleep(shortDelayMs); // make sure actor has started
    alarmService.setSeverity(key2, JSeverityLevel.Okay);
    alarmService.setSeverity(key3, JSeverityLevel.Okay);
    Thread.sleep(shortDelayMs);
    assertEquals(alarmService.getHealth(nfKey), JHealth.Good);
    Thread.sleep(shortDelayMs);
    assertEquals(callbackHealth.get(), JHealth.Good);

    Thread.sleep(delayMs); // wait for severity to expire and become "Disconnected"
    assertEquals(callbackHealth.get(), JHealth.Bad);
    assertEquals(alarmService.getHealth(nfKey), JHealth.Bad);
    assertEquals(alarmService.getHealth(JAlarmKey.create()), JHealth.Bad);

    alarmService.setSeverity(key2, JSeverityLevel.Major);
    alarmService.setSeverity(key3, JSeverityLevel.Okay);

    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(callbackHealth.get(), JHealth.Ill);
    assertEquals(alarmService.getHealth(nfKey), JHealth.Ill);
    alarmService.acknowledgeAndResetAlarm(key2);

    alarmService.setSeverity(key2, JSeverityLevel.Okay);
    alarmService.setSeverity(key3, JSeverityLevel.Critical);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(callbackHealth.get(), JHealth.Bad);
    assertEquals(alarmService.getHealth(nfKey), JHealth.Bad);

    // Stop the actors monitoring the alarm and health
    healthMonitor.stop();
  }
}

