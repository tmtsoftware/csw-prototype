package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.examples.vsliceJava.TestEnv;
import csw.services.apps.containerCmd.ContainerCmd;
import csw.services.ccs.AssemblyController.Submit;
import csw.services.ccs.CommandStatus.CommandResult;
import csw.services.ccs.CommandStatus.NoLongerValid;
import csw.services.ccs.Validation.WrongInternalStateIssue;
import csw.services.loc.LocationService;
import csw.services.pkg.Component.AssemblyInfo;
import csw.util.config.Configurations;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Configurations.SetupConfigArg;
import csw.util.config.Events.SystemEvent;
import javacsw.services.events.IEventService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static javacsw.services.ccs.JCommandStatus.*;
import static javacsw.services.pkg.JSupervisor.*;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

@SuppressWarnings({"WeakerAccess", "OptionalUsedAsFieldOrParameterType", "MismatchedReadAndWriteOfArray"})
public class TromboneAssemblyBasicTests extends JavaTestKit {
  private static ActorSystem system;
  private static LoggingAdapter logger;

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;
  private static Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));
  private static IEventService eventService;

  // List of top level actors that were created for the HCD (for clean up)
  private static List<ActorRef> hcdActors = Collections.emptyList();


  public TromboneAssemblyBasicTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);
    TestEnv.createTromboneAssemblyConfig(system);
    eventService = IEventService.getEventService(IEventService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);

    // Starts the HCD used in the test
    Map<String, String> configMap = Collections.singletonMap("", "tromboneHCD.conf");
    ContainerCmd cmd = new ContainerCmd("vsliceJava", new String[]{"--standalone"}, configMap);
    hcdActors = cmd.getActors();
    if (hcdActors.size() == 0) logger.error("Failed to create trombone HCD");
  }

  @AfterClass
  public static void teardown() {
    hcdActors.forEach(TromboneAssemblyBasicTests::cleanup);
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  Props getTromboneProps(AssemblyInfo assemblyInfo, Optional<ActorRef> supervisorIn) {
    if (!supervisorIn.isPresent()) return TromboneAssembly.props(assemblyInfo, new TestProbe(system).ref());
    return TromboneAssembly.props(assemblyInfo, supervisorIn.get());
  }

  ActorRef newTrombone(ActorRef supervisor) {
    Props props = getTromboneProps(assemblyContext.info, Optional.of(supervisor));
    return system.actorOf(props);
  }

  // Stop any actors created for a test to avoid conflict with other tests
  private static void cleanup(ActorRef... a) {
    TestProbe monitor = new TestProbe(system);
    for(ActorRef actorRef : a) {
      monitor.watch(actorRef);
      system.stop(actorRef);
      monitor.expectTerminated(actorRef, timeout.duration());
    }
  }

  // --- low-level instrumented trombone assembly tests ---

  @Test
  public void test2() {
    // should lifecycle properly with a fake supervisor
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tla = newTrombone(fakeSupervisor.ref());

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(duration("10 seconds"), Started);

    fakeSupervisor.send(tla, Running);

    fakeSupervisor.send(tla, DoShutdown);
    fakeSupervisor.expectMsg(ShutdownComplete);

    logger.info("Shutdown Complete");
    cleanup(tla);
  }

  @Test
  public void test3() {
    // datum without an init should fail
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    //val fakeSupervisor = TestProbe()
    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", new SetupConfig(assemblyContext.datumCK.prefix()));

    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification succeeds because verification does not look at state
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);
    logger.info("Accepted: " + acceptedMsg);

    // This should fail due to wrong internal state
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    System.out.println("XXX " + ((NoLongerValid) completeMsg.details().status(0)).issue());
    assertEquals(completeMsg.overall(), Incomplete);
    assertTrue(completeMsg.details().status(0) instanceof NoLongerValid);
    assertTrue(((NoLongerValid) completeMsg.details().status(0)).issue() instanceof WrongInternalStateIssue);

    cleanup(tromboneAssembly);
  }

  @Test
  public void test4() {
    // should allow a datum

    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));

    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);

    CommandResult completeMsg = fakeClient.expectMsgClass(duration("5 seconds"), CommandResult.class);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().status(0), Completed);
    // Wait a bit to see if there is any spurious messages
    fakeClient.expectNoMsg(duration("250 milli"));
    //logger.info("Completed: " + completeMsg)

    cleanup(tromboneAssembly);
  }

  @Test
  public void test5() {
    // should show a move without a datum as an error because trombone in wrong state

    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.send(tromboneAssembly, Running);

    // Sending an Init first so we can see the dataum issue
    double testPosition = 90.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()), assemblyContext.moveSC(testPosition));

    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg1: " + acceptedMsg);
    assertEquals(acceptedMsg.overall(), Accepted);
    // This should fail due to wrong internal state
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("Completed Msg: " + completeMsg);
    assertEquals(completeMsg.overall(), Incomplete);
    // First completes no issue
    assertEquals(completeMsg.details().status(0), Completed);
    // Second is for move and it should be invalid
    assertTrue(completeMsg.details().status(1) instanceof NoLongerValid);
    assertTrue(((NoLongerValid) completeMsg.details().status(1)).issue() instanceof WrongInternalStateIssue);

    cleanup(tromboneAssembly);
  }

  @Test
  public void test6() {
    // should allow an init, datum then 2 moves
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    double testMove = 90.0;
    double testMove2 = 100.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()),
      assemblyContext.moveSC(testMove),
      assemblyContext.moveSC(testMove2));

    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg2: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().results().size(), sca.configs().size());

    cleanup(tromboneAssembly);
  }

  @Test
  public void test7() {
    // should allow an init, datum then a position
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    double testRangeDistance = 125.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()),
      assemblyContext.positionSC(testRangeDistance));

    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("msg1: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg2: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().results().size(), sca.configs().size());

    cleanup(tromboneAssembly);
  }

  @Test
  public void test8() {
    // should allow an init, datum then a set of positions as separate sca
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));
    fakeClient.send(tromboneAssembly, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("completeMsg: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);

    // This will send a config arg with 10 position commands
    int[] testRangeDistance = new int[]{90, 100, 110, 120, 130, 140, 150, 160, 170, 180};
    List<SetupConfig> positionConfigs = Arrays.stream(testRangeDistance).mapToObj(f -> assemblyContext.positionSC(f))
      .collect(Collectors.toList());

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", positionConfigs);
    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones - give this some extra time to complete
    completeMsg = fakeClient.expectMsgClass(duration("10 seconds"), CommandResult.class);
    logger.info("completeMsg: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().results().size(), sca.configs().size());

    cleanup(tromboneAssembly);
  }

  @Test
  public void test9() {
    // should allow an init, datum then move and stop
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));
    fakeClient.send(tromboneAssembly, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("completeMsg: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);

    // Now start a long move
    double testMove = 150.1;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", assemblyContext.moveSC(testMove));
    // Send the move
    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);

    // Now send the stop after a bit of delay to let it get going
    // This is a timing thing that may not work on all machines
    fakeSupervisor.expectNoMsg(duration("200 millis"));
    SetupConfigArg stop = Configurations.createSetupConfigArg("testobsId", new SetupConfig(assemblyContext.stopCK.prefix()));
    // Send the stop
    fakeClient.send(tromboneAssembly, new Submit(stop));

    // Stop must be accepted too
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("acceptedmsg2: " + acceptedMsg);
    assertEquals(completeMsg.overall(), AllCompleted);

    // Second one is completion of the stop
    completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg22: " + completeMsg);
    assertEquals(completeMsg.overall(), Incomplete);
    assertEquals(completeMsg.details().status(0), Cancelled);
    // Checking that no talking
    fakeClient.expectNoMsg(duration("100 milli"));

    cleanup(tromboneAssembly);
  }

  @Test
  public void test10() throws InterruptedException {
    // should allow an init, setElevation
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    // XXX
    FiniteDuration d = duration("10 seconds");

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(d, Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));
    fakeClient.send(tromboneAssembly, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(d, CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(d, CommandResult.class);
    assertEquals(completeMsg.overall(), AllCompleted);

    double testEl = 150.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", assemblyContext.setElevationSC(testEl));

    // Send the setElevation
    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);
    //logger.info(s"AcceptedMsg: $acceptedMsg")

    // Second one is completion of the executed ones
    completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("completeMsg: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().results().size(), sca.configs().size());

    cleanup(tromboneAssembly);
  }

  @Test
  public void test11() {
    // should get an error for SetAngle without fillowing after good setup
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(duration("10 seconds"), Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));
    fakeClient.send(tromboneAssembly, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(completeMsg.overall(), AllCompleted);

    // Now try a setAngle
    double setAngleValue = 22.0;
    SetupConfigArg sca2 = Configurations.createSetupConfigArg("testobsId", assemblyContext.setAngleSC(setAngleValue));
    // Send the command
    fakeClient.send(tromboneAssembly, new Submit(sca2));

    // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg1: " + acceptedMsg);
    assertEquals(acceptedMsg.overall(), Accepted);

    // This should fail due to wrong internal state
    completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("Completed Msg2: " + completeMsg);

    assertEquals(completeMsg.overall(), Incomplete);
    // First is not valid
    assertTrue(completeMsg.details().status(0) instanceof NoLongerValid);
    assertTrue(((NoLongerValid) completeMsg.details().status(0)).issue() instanceof WrongInternalStateIssue);

    cleanup(tromboneAssembly);
  }

  @Test
  public void test12() {
    // should allow an init, setElevation, follow, stop
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));
    fakeClient.send(tromboneAssembly, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(completeMsg.overall(), AllCompleted);

    double testEl = 150.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      assemblyContext.setElevationSC(testEl), assemblyContext.followSC(false),
      new SetupConfig(assemblyContext.stopCK.prefix()));

    // Send the setElevation
    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);
    //logger.info(s"AcceptedMsg: $acceptedMsg")

    // Second one is completion of the executed ones
    completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("completeMsg: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().results().size(), sca.configs().size());

    cleanup(tromboneAssembly);
  }

  @Test
  public void test() {
    // should allow an init, setElevation, follow, a bunch of events and a stop
    TestProbe fakeSupervisor = new TestProbe(system);
    ActorRef tromboneAssembly = newTrombone(fakeSupervisor.ref());
    TestProbe fakeClient = new TestProbe(system);

    fakeSupervisor.expectMsg(Initialized);
    fakeSupervisor.expectMsg(duration("10 seconds"), Started);
    fakeSupervisor.expectNoMsg(duration("200 milli"));
    fakeSupervisor.send(tromboneAssembly, Running);

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()),
      new SetupConfig(assemblyContext.datumCK.prefix()));
    fakeClient.send(tromboneAssembly, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    //logger.info("acceptedMsg: " + acceptedMsg)
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones
    CommandResult completeMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(completeMsg.overall(), AllCompleted);

    double testEl = 150.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      assemblyContext.setElevationSC(testEl), assemblyContext.followSC(false));

    // Send the setElevation
    fakeClient.send(tromboneAssembly, new Submit(sca));

    // This first one is the accept/verification
    acceptedMsg = fakeClient.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);
    //logger.info(s"AcceptedMsg: $acceptedMsg")

    // Second one is completion of the executed ones
    completeMsg = fakeClient.expectMsgClass(duration("10 seconds"), CommandResult.class);
    logger.info("completeMsg: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);

    // Now send some events
    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
    IEventService tcsRtc = eventService;

    double testFE = 10.0;
    // Publish a single focus error. This will generate a published event
    tcsRtc.publish(new SystemEvent(AssemblyContext.focusErrorPrefix).add(fe(testFE)));

    double[] testZenithAngles = new double[]{0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0};
    // These are fake messages for the FollowActor that will be sent to simulate the TCS
    List<SystemEvent> tcsEvents = Arrays.stream(testZenithAngles).mapToObj(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    // This should result in the length of tcsEvents being published
    tcsEvents.forEach(f -> {
      logger.info("Publish: " + f);
      tcsRtc.publish(f);
    });

    expectNoMsg(duration("10 seconds"));

    cleanup(tromboneAssembly);
  }
}
