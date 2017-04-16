package javacsw.services.alarms.tests;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import csw.services.alarms.AlarmKey;
import csw.services.alarms.AscfValidation;
import javacsw.services.alarms.*;
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
@SuppressWarnings("ALL")
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
    System.setProperty("csw.services.alarms.refreshSecs", String.valueOf(refreshSecs));

    // Later, in another JVM...,
    // Get the alarm service by looking up the name with the location service (using a small value for refreshSecs for testing)
    alarmService = IBlockingAlarmService.getAlarmService(IBlockingAlarmService.defaultName, system, timeout);
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  // For testing callback
  private static CurrentSeverity callbackSev = new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected);
  private static Optional<Health> callbackHealth = Optional.empty();

  // Called when alarm severity changes
  static IAlarmServiceAdmin.AlarmHandler alarmHandler = new IAlarmServiceAdmin.AlarmHandler() {
    public void handleAlarmStatus(AlarmStatus alarmStatus) {
      AlarmKey a = alarmStatus.alarmKey();
      logger.debug("Alarm Status: " + a.subsystem() + ":" + a.component() + ":" + a.name() + ": " + alarmStatus.currentSeverity());
      callbackSev = alarmStatus.currentSeverity();
    }
  };

  // Called when health severity changes
  static IAlarmServiceAdmin.HealthHandler healthHandler = new IAlarmServiceAdmin.HealthHandler() {
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
    List<AlarmModel> alarms = admin.getAlarms(JAlarmKey.create());
    for (AlarmModel alarm : alarms) {
      // XXX TODO: compare results
      logger.debug("List Alarm: " + alarm);
    }

    // Test working with an alarm and monitoring the alarm severity level
    AlarmKey key1 = new AlarmKey("TCS", "tcsPk", "cpuExceededAlarm");
    AlarmKey key2 = new AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisHighLimitAlarm");
    AlarmKey key3 = new AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisLowLimitAlarm");

    AlarmMonitor alarmMonitor = admin.monitorAlarms(key1, alarmHandler, healthHandler, false);
    Thread.sleep(shortDelayMs); // make sure actor has started

    alarmService.setSeverity(key1, JSeverityLevel.Critical);
    Thread.sleep(delayMs); // wait for severity to expire

    // alarm is latched, so stays at critical
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Critical));
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Critical));

    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    // alarm is latched, so stays at critical
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Critical));
    Thread.sleep(shortDelayMs);
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Critical));

    // Acknowledge the alarm, which clears it, resets it back to Okay
    admin.acknowledgeAndResetAlarm(key1);
    alarmService.setSeverity(key1, JSeverityLevel.Okay);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback, so the test below passes
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Okay, JSeverityLevel.Okay)); // alarm was cleared
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Okay, JSeverityLevel.Okay));

    Thread.sleep(delayMs); // wait for severity to expire and become "Disconnected"
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected)); // alarm severity key expired
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected));

    // Test alarm in shelved state
    admin.setShelvedState(key1, JShelvedState.Shelved);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    // getSeverity should return the severity that was set ...
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    // but the callback should not have been called, since alarm is shelved (callbackSev should have previous value)
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Disconnected, JSeverityLevel.Disconnected));
    // un-shelve the alarm and try it again
    admin.setShelvedState(key1, JShelvedState.Normal);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    // Since the alarm is no longer shelved, the callback should be called this time
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));

    // Test alarm in deactivated state
    admin.acknowledgeAndResetAlarm(key1);
    alarmService.setSeverity(key1, JSeverityLevel.Okay);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    admin.setActivationState(key1, JActivationState.OutOfService);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    // callback should not have been called, callbackSev should have previous value
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Okay, JSeverityLevel.Okay));
    // reactivate the alarm
    admin.setActivationState(key1, JActivationState.Normal);
    alarmService.setSeverity(key1, JSeverityLevel.Warning);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    // This time the callback should have been called
    assertEquals(callbackSev, new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));
    assertEquals(admin.getSeverity(key1), new CurrentSeverity(JSeverityLevel.Warning, JSeverityLevel.Warning));

    // Test health monitor
    alarmMonitor.stop();
    AlarmKey nfKey = JAlarmKey.create(Optional.of("nfiraos"));
    AlarmMonitor healthMonitor = admin.monitorAlarms(nfKey, alarmHandler, healthHandler, false);
    Thread.sleep(shortDelayMs); // make sure actor has started
    alarmService.setSeverity(key2, JSeverityLevel.Okay);
    alarmService.setSeverity(key3, JSeverityLevel.Okay);
    Thread.sleep(shortDelayMs);
    assertEquals(admin.getHealth(nfKey), JHealth.Good);
    Thread.sleep(shortDelayMs);
    assertEquals(callbackHealth.get(), JHealth.Good);

    Thread.sleep(delayMs); // wait for severity to expire and become "Disconnected"
    assertEquals(callbackHealth.get(), JHealth.Bad);
    assertEquals(admin.getHealth(nfKey), JHealth.Bad);
    assertEquals(admin.getHealth(JAlarmKey.create()), JHealth.Bad);

    alarmService.setSeverity(key2, JSeverityLevel.Major);
    alarmService.setSeverity(key3, JSeverityLevel.Okay);

    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(callbackHealth.get(), JHealth.Ill);
    assertEquals(admin.getHealth(nfKey), JHealth.Ill);
    admin.acknowledgeAndResetAlarm(key2);

    alarmService.setSeverity(key2, JSeverityLevel.Okay);
    alarmService.setSeverity(key3, JSeverityLevel.Critical);
    Thread.sleep(shortDelayMs); // Give redis time to notify the callback
    assertEquals(callbackHealth.get(), JHealth.Bad);
    assertEquals(admin.getHealth(nfKey), JHealth.Bad);

    // Stop the actors monitoring the alarm and health
    healthMonitor.stop();
  }
}

