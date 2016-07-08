package javacsw.services.alarms;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import csw.services.alarms.AlarmKey;
import javacsw.services.alarms.JAlarmModel.*;
import javacsw.services.alarms.JAlarmState.*;
import csw.services.alarms.AlarmModel;
import csw.services.alarms.AlarmModel.*;
import csw.services.alarms.AlarmService;
import csw.services.alarms.AlarmService.*;
import csw.services.loc.LocationService;
import csw.services.trackLocation.TrackLocation;
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
public class JAlarmServiceTests {
    private static ActorSystem system;
    private static LoggingAdapter logger;
    private static IAlarmService alarmService;
    private static Timeout timeout = new Timeout(FiniteDuration.create(60, TimeUnit.SECONDS));

    // Set a low refresh rate for the test
    private static int refreshSecs = 1;

    // Time until alarm severity expires
    private static int expireSecs = refreshSecs * IAlarmService.maxMissedRefresh;

    // Time in ms to wait for a Redis callback
    private static int shortDelayMs = 500;

    // Time in ms to wait to see if an alarm severity expired
    private static int delayMs = expireSecs * 1000 + shortDelayMs;

    public JAlarmServiceTests() throws URISyntaxException {
    }

    @BeforeClass
    public static void setup() throws ExecutionException, InterruptedException {
        LocationService.initInterface();
        system = ActorSystem.create();

        String asName = "Alarm Service Test";
        int port = 7777;

        // Note: This part is only for testing: Normally Redis would already be running and registered with the location service.
        // Start redis and register it with the location service on port 7777.
        // The following is the equivalent of running this from the command line:
        //   tracklocation --name "Alarm Service Test" --command "redis-server --port 7777" --port 7777
        Runnable task = () -> TrackLocation.main(new String[]{
                "--name", asName,
                "--command", "redis-server --port " + port,
                "--port", String.valueOf(port)
        });
        Thread thread = new Thread(task);
        thread.start();

        // Later, in another JVM...,
        // Get the alarm service by looking up the name with the location service (using a small value for refreshSecs for testing)
        alarmService = IAlarmService.getAlarmService(asName, refreshSecs, system, timeout).get();
    }

    @AfterClass
    public static void teardown() {
        logger.info("XXX In Teardown!");
        alarmService.shutdown();
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    // For testing callback
    private static SeverityLevel callbackSev = JSeverityLevel.Indeterminate;
    private static Optional<Health> callbackHealth = Optional.empty();

    // Called when alarm severity changes
    static IAlarmService.AlarmHandler alarmHandler = new IAlarmService.AlarmHandler() {
        public void handleAlarmStatus(AlarmStatus alarmStatus) {
            AlarmKey a = alarmStatus.alarmKey();
            logger.info("Alarm Status: " + a.subsystem() + ":" + a.component() + ":" + a.name() + ": " + alarmStatus.severity());
            callbackSev = alarmStatus.severity();
        }
    };

    // Called when health severity changes
    static IAlarmService.HealthHandler healthHandler = new IAlarmService.HealthHandler() {
        public void handleHealthStatus(HealthStatus healthStatus) {
            AlarmKey a = healthStatus.key();
            logger.info("Health Status: " + a.subsystem() + ":" + a.component() + ":" + a.name() + ": " + healthStatus.health());
            callbackHealth = Optional.of(healthStatus.health());
        }
    };

    // Note: In the tests below we just call .get() on the future results for simplicity.
    // In a real application, you could use other methods...

    @Test
    public void basicJavaTests() throws Exception {
        logger = Logging.getLogger(system, this);
        // Get the test alarm service config file (ascf)
        URL url = JAlarmServiceTests.class.getResource("/test-alarms.conf");
        File ascf = Paths.get(url.toURI()).toFile();

        // initialize the list of alarms in Redis
        List<AlarmService.Problem> problems = alarmService.initAlarms(ascf, false).get();
        JProblem.printProblems(problems);
        assertTrue(JProblem.errorCount(problems) == 0);

        // List all the alarms that were written to Redis
        List<AlarmModel> alarms = alarmService.getAlarms(JAlarmKey.create()).get();
        for (AlarmModel alarm : alarms) {
            // XXX TODO: compare results
            logger.info("List Alarm: " + alarm);
        }

        // Test working with an alarm and monitoring the alarm severity level
        AlarmKey key1 = new AlarmKey("TCS", "tcsPk", "cpuExceededAlarm");
        AlarmKey key2 = new AlarmKey("NFIRAOS", "envCtrl", "minTemperature");
        AlarmKey key3 = new AlarmKey("NFIRAOS", "envCtrl", "maxTemperature");

        AlarmMonitor alarmMonitor = alarmService.monitorHealth(key1,
                Optional.empty(), Optional.of(alarmHandler), Optional.of(healthHandler));
        Thread.sleep(shortDelayMs); // make sure actor has started

        alarmService.setSeverity(key1, JSeverityLevel.Critical).get();
        Thread.sleep(delayMs); // wait for severity to expire

        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Critical); // alarm is latched, so stays at critical
        assertEquals(callbackSev, JSeverityLevel.Critical);

        alarmService.setSeverity(key1, JSeverityLevel.Warning).get();
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Critical); // alarm is latched, so stays at critical
        assertEquals(callbackSev, JSeverityLevel.Critical);

        // Acknowledge the alarm, which clears it, resets it back to Okay
        alarmService.acknowledgeAlarm(key1).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback, so the test below passes
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Okay); // alarm was cleared
        assertEquals(callbackSev, JSeverityLevel.Okay);

        Thread.sleep(delayMs); // wait for severity to expire and become "Indeterminate"
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Indeterminate); // alarm severity key expired
        assertEquals(callbackSev, JSeverityLevel.Indeterminate);

        // Test alarm in shelved state
        alarmService.setShelvedState(key1, JShelvedState.Shelved).get();
        alarmService.setSeverity(key1, JSeverityLevel.Warning).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Warning);
        assertEquals(callbackSev, JSeverityLevel.Indeterminate);
        alarmService.setShelvedState(key1, JShelvedState.Normal).get();
        alarmService.setSeverity(key1, JSeverityLevel.Warning).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        assertEquals(callbackSev, JSeverityLevel.Warning);
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Warning);

        // Test alarm in deactivated state
        alarmService.acknowledgeAlarm(key1).get();
        alarmService.setSeverity(key1, JSeverityLevel.Okay).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        alarmService.setActivationState(key1, JActivationState.OutOfService).get();
        alarmService.setSeverity(key1, JSeverityLevel.Warning).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Warning);
        assertEquals(callbackSev, JSeverityLevel.Okay);
        alarmService.setActivationState(key1, JActivationState.Normal).get();
        alarmService.setSeverity(key1, JSeverityLevel.Warning).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        assertEquals(callbackSev, JSeverityLevel.Warning);
        assertEquals(alarmService.getSeverity(key1).get(), JSeverityLevel.Warning);

        // Test health monitor
        alarmMonitor.stop();
        AlarmKey nfKey = JAlarmKey.create(Optional.of("NFIRAOS"));
        AlarmMonitor healthMonitor = alarmService.monitorHealth(nfKey,
                Optional.empty(), Optional.of(alarmHandler), Optional.of(healthHandler));
        alarmService.setSeverity(key2, JSeverityLevel.Okay).get();
        alarmService.setSeverity(key3, JSeverityLevel.Okay).get();
        Thread.sleep(shortDelayMs); // make sure actor has started
        assertEquals(callbackHealth.get(), JHealth.Good); // XXX failed here once: need more delay?
        assertEquals(alarmService.getHealth(nfKey).get(), JHealth.Good);

        Thread.sleep(delayMs); // wait for severity to expire and become "Indeterminate"
        assertEquals(callbackHealth.get(), JHealth.Bad);
        assertEquals(alarmService.getHealth(nfKey).get(), JHealth.Bad);
        assertEquals(alarmService.getHealth(JAlarmKey.create()).get(), JHealth.Bad);

        alarmService.setSeverity(key2, JSeverityLevel.Major).get();
        alarmService.setSeverity(key3, JSeverityLevel.Okay).get();

        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        assertEquals(callbackHealth.get(), JHealth.Ill);
        assertEquals(alarmService.getHealth(nfKey).get(), JHealth.Ill);
        alarmService.acknowledgeAlarm(key2).get();

        alarmService.setSeverity(key2, JSeverityLevel.Okay).get();
        alarmService.setSeverity(key3, JSeverityLevel.Critical).get();
        Thread.sleep(shortDelayMs); // Give redis time to notify the callback
        assertEquals(callbackHealth.get(), JHealth.Bad);
        assertEquals(alarmService.getHealth(nfKey).get(), JHealth.Bad);

        // Stop the actors monitoring the alarm and health
        healthMonitor.stop();
    }
}

