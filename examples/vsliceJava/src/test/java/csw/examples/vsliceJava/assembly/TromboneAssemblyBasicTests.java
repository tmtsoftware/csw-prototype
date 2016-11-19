package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.services.apps.containerCmd.ContainerCmd;
import csw.services.loc.LocationService;
import csw.services.pkg.Component.AssemblyInfo;
import javacsw.services.events.IEventService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static javacsw.services.pkg.JSupervisor.*;

@SuppressWarnings({"WeakerAccess", "OptionalUsedAsFieldOrParameterType", "MismatchedReadAndWriteOfArray"})
public class TromboneAssemblyBasicTests extends JavaTestKit {
  private static ActorSystem system;
  private static LoggingAdapter logger;

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;
  private static Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));
  private static IEventService eventService;

  // List of top level actors that were created for the HCD (for clean up)
  private static List<ActorRef> hcdActors;


  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public TromboneAssemblyBasicTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);
    eventService = IEventService.getEventService(IEventService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);

    // Starts the HCD used in the test
    Map<String, String> configMap = Collections.singletonMap("", "tromboneHCD.conf");
    ContainerCmd cmd = new ContainerCmd("vslice", new String[]{"--standalone"}, configMap);
    hcdActors = cmd.getActors();

  }

  @AfterClass
  public static void teardown() {
    hcdActors.forEach(actorRef -> actorRef.tell(PoisonPill.getInstance(), ActorRef.noSender()));
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  Props getTromboneProps(AssemblyInfo assemblyInfo, Optional<ActorRef> supervisorIn) {
    if (!supervisorIn.isPresent()) return TromboneAssembly.props(assemblyInfo, new TestProbe(system).ref());
    return TromboneAssembly.props(assemblyInfo, supervisorIn.get());
  }

  ActorRef newTrombone(ActorRef supervisor, AssemblyInfo assemblyInfo) {
    Props props = getTromboneProps(assemblyInfo, Optional.of(supervisor));
    return system.actorOf(props);
  }

  ActorRef newTrombone(ActorRef supervisor) {
    Props props = getTromboneProps(assemblyContext.info, Optional.of(supervisor));
    return system.actorOf(props);
  }

  TestActorRef<TromboneAssembly> newTestTrombone(ActorRef supervisor, AssemblyInfo assemblyInfo) {
    Props props = getTromboneProps(assemblyInfo, Optional.of(supervisor));
    return TestActorRef.create(system, props);
  }

  TestActorRef<TromboneAssembly> newTestTrombone(ActorRef supervisor) {
    Props props = getTromboneProps(assemblyContext.info, Optional.of(supervisor));
    return TestActorRef.create(system, props);
  }

  // --- low-level instrumented trombone assembly tests ---

//  @Test
//  public void test1() {
//    // should get initialized with configs from files (same as AlgorithmData
//      val supervisor = TestProbe()
//      val tla = newTestTrombone(supervisor.ref)
//
//      tla.underlyingActor.controlConfig.stageZero should be(AssemblyTestData.TestControlConfig.stageZero)
//      tla.underlyingActor.controlConfig.positionScale should be(AssemblyTestData.TestControlConfig.positionScale)
//      tla.underlyingActor.controlConfig.minStageEncoder should be(AssemblyTestData.TestControlConfig.minStageEncoder)
//
//      tla.underlyingActor.calculationConfig.defaultInitialElevation should be(AssemblyTestData.TestCalculationConfig.defaultInitialElevation)
//      tla.underlyingActor.calculationConfig.focusErrorGain should be(AssemblyTestData.TestCalculationConfig.focusErrorGain)
//      tla.underlyingActor.calculationConfig.lowerFocusLimit should be(AssemblyTestData.TestCalculationConfig.lowerFocusLimit)
//      tla.underlyingActor.calculationConfig.upperFocusLimit should be(AssemblyTestData.TestCalculationConfig.upperFocusLimit)
//      tla.underlyingActor.calculationConfig.zenithFactor should be(AssemblyTestData.TestCalculationConfig.zenithFactor)
//
//      expectNoMsg(2.seconds)
//    }

  @Test
  public void test2() {
    // should lifecycle properly with a fake supervisor
    TestProbe fakeSupervisor = new TestProbe(system);
    TestActorRef<TromboneAssembly> tla = newTestTrombone(fakeSupervisor.ref());

      fakeSupervisor.expectMsg(Initialized);
      fakeSupervisor.expectMsg(duration("10 seconds"), Started);

      fakeSupervisor.send(tla, Running);

      fakeSupervisor.send(tla, DoShutdown);
      fakeSupervisor.expectMsg(ShutdownComplete);
      logger.info("Shutdown Complete");
    }

//    it("datum without an init should fail") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK))
//
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification succeeds because verification does not look at state
//      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      logger.info(s"Accepted: $acceptedMsg")
//
//      // This should fail due to wrong internal state
//      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe Incomplete
//      completeMsg.details.status(0) shouldBe a[NoLongerValid]
//      completeMsg.details.status(0).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow a datum") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//
//      val completeMsg = fakeClient.expectMsgClass(5.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.status(0) shouldBe Completed
//      // Wait a bit to see if there is any spurious messages
//      fakeClient.expectNoMsg(250.milli)
//      //logger.info("Completed: " + completeMsg)
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should show a move without a datum as an error because trombone in wrong state") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      // Sending an Init first so we can see the dataum issue
//      val testPosition = 90.0
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), moveSC(testPosition))
//
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
//      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("msg1: " + acceptedMsg)
//      acceptedMsg.overall shouldBe Accepted
//      // This should fail due to wrong internal state
//      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("Completed Msg: " + completeMsg)
//      completeMsg.overall shouldBe Incomplete
//      // First completes no issue
//      completeMsg.details.status(0) shouldBe Completed
//      // Second is for move and it should be invalid
//      completeMsg.details.status(1) shouldBe a[NoLongerValid]
//      completeMsg.details.status(1).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, datum then 2 moves") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val testMove = 90.0
//      val testMove2 = 100.0
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK), moveSC(testMove), moveSC(testMove2))
//
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//
//      // Second one is completion of the executed ones
//      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("msg2: " + completeMsg)
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.results.size shouldBe sca.configs.size
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, datum then a position") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val testRangeDistance = 125.0
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK), positionSC(testRangeDistance))
//
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      //logger.info("msg1: " + acceptedMsg)
//      acceptedMsg.overall shouldBe Accepted
//
//      // Second one is completion of the executed ones
//      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("msg2: " + completeMsg)
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.results.size shouldBe sca.configs.size
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, datum then a set of positions as separate sca") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//      fakeClient.send(tromboneAssembly, Submit(datum))
//
//      // This first one is the accept/verification
//      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      //logger.info("acceptedMsg: " + acceptedMsg)
//      acceptedMsg.overall shouldBe Accepted
//
//      // Second one is completion of the executed ones
//      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("completeMsg: " + completeMsg)
//      completeMsg.overall shouldBe AllCompleted
//
//      // This will send a config arg with 10 position commands
//      val testRangeDistance = 90 to 180 by 10
//      val positionConfigs = testRangeDistance.map(f => positionSC(f))
//
//      val sca = Configurations.createSetupConfigArg("testobsId", positionConfigs: _*)
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      //logger.info("acceptedMsg: " + acceptedMsg)
//      acceptedMsg.overall shouldBe Accepted
//
//      // Second one is completion of the executed ones - give this some extra time to complete
//      completeMsg = fakeClient.expectMsgClass(10.seconds, classOf[CommandResult])
//      logger.info("completeMsg: " + completeMsg)
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.results.size shouldBe sca.configs.size
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, datum then move and stop") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//      fakeClient.send(tromboneAssembly, Submit(datum))
//
//      // This first one is the accept/verification
//      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      // Second one is completion of the executed datum
//      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe AllCompleted
//
//      // Now start a long move
//      val testMove = 150.1
//      val sca = Configurations.createSetupConfigArg("testobsId", moveSC(testMove))
//      // Send the move
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//
//      // Now send the stop after a bit of delay to let it get going
//      // This is a timing thing that may not work on all machines
//      fakeSupervisor.expectNoMsg(200.millis)
//      val stop = Configurations.createSetupConfigArg("testobsId", SetupConfig(stopCK))
//      // Send the stop
//      fakeClient.send(tromboneAssembly, Submit(stop))
//
//      // Stop must be accepted too
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("acceptedmsg2: " + acceptedMsg)
//      completeMsg.overall shouldBe AllCompleted
//
//      // Second one is completion of the stop
//      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("msg22: " + completeMsg)
//      completeMsg.overall shouldBe Incomplete
//      completeMsg.details.status(0) shouldBe Cancelled
//      // Checking that no talking
//      fakeClient.expectNoMsg(100.milli)
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, setElevation") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//      fakeClient.send(tromboneAssembly, Submit(datum))
//
//      // This first one is the accept/verification
//      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      // Second one is completion of the executed datum
//      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe AllCompleted
//
//      val testEl = 150.0
//      val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl))
//
//      // Send the setElevation
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      //logger.info(s"AcceptedMsg: $acceptedMsg")
//
//      // Second one is completion of the executed ones
//      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info(s"completeMsg: $completeMsg")
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.results.size shouldBe sca.configs.size
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should get an error for SetAngle without fillowing after good setup") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      // Sending an Init first so we can see the datum issue
//      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
//      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("acceptedMsg1: " + acceptedMsg)
//      acceptedMsg.overall shouldBe Accepted
//
//      // Second one is completion of the executed init/datum
//      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe AllCompleted
//      logger.info(s"completedMsg1: $completeMsg")
//
//      // Now try a setAngle
//      val setAngleValue = 22.0
//      val sca2 = Configurations.createSetupConfigArg("testobsId", setAngleSC(setAngleValue))
//      // Send the command
//      fakeClient.send(tromboneAssembly, Submit(sca2))
//
//      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("msg1: " + acceptedMsg)
//      acceptedMsg.overall shouldBe Accepted
//
//      // This should fail due to wrong internal state
//      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info("Completed Msg2: " + completeMsg)
//
//      completeMsg.overall shouldBe Incomplete
//      // First is not valid
//      completeMsg.details.status(0) shouldBe a[NoLongerValid]
//      completeMsg.details.status(0).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, setElevation, follow, stop") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//      fakeClient.send(tromboneAssembly, Submit(datum))
//
//      // This first one is the accept/verification
//      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      // Second one is completion of the executed datum
//      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe AllCompleted
//
//      val testEl = 150.0
//      val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl), followSC(false), SetupConfig(stopCK))
//
//      // Send the setElevation
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      //logger.info(s"AcceptedMsg: $acceptedMsg")
//
//      // Second one is completion of the executed ones
//      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info(s"completeMsg: $completeMsg")
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.results.size shouldBe sca.configs.size
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//
//    it("should allow an init, setElevation, follow, 2 setAngles, and a stop") {
//      val fakeSupervisor = TestProbe()
//      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//      val fakeClient = TestProbe()
//
//      //val fakeSupervisor = TestProbe()
//      fakeSupervisor.expectMsg(Initialized)
//      fakeSupervisor.expectMsg(Started)
//      fakeSupervisor.expectNoMsg(200.milli)
//      fakeSupervisor.send(tromboneAssembly, Running)
//
//      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//      fakeClient.send(tromboneAssembly, Submit(datum))
//
//      // This first one is the accept/verification
//      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      // Second one is completion of the executed datum
//      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      completeMsg.overall shouldBe AllCompleted
//
//      val testEl = 150.0
//      val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl), followSC(false), SetupConfig(stopCK))
//
//      // Send the setElevation
//      fakeClient.send(tromboneAssembly, Submit(sca))
//
//      // This first one is the accept/verification
//      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      acceptedMsg.overall shouldBe Accepted
//      //logger.info(s"AcceptedMsg: $acceptedMsg")
//
//      // Second one is completion of the executed ones
//      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//      logger.info(s"completeMsg: $completeMsg")
//      completeMsg.overall shouldBe AllCompleted
//      completeMsg.details.results.size shouldBe sca.configs.size
//
//      val monitor = TestProbe()
//      monitor.watch(tromboneAssembly)
//      system.stop(tromboneAssembly)
//      monitor.expectTerminated(tromboneAssembly)
//    }
//  }
//
//  it("should allow an init, setElevation, follow, a bunch of events and a stop") {
//
//    val fakeSupervisor = TestProbe()
//    val tromboneAssembly = newTrombone(fakeSupervisor.ref)
//    val fakeClient = TestProbe()
//
//    //val fakeSupervisor = TestProbe()
//    fakeSupervisor.expectMsg(Initialized)
//    fakeSupervisor.expectMsg(Started)
//    fakeSupervisor.expectNoMsg(200.milli)
//    fakeSupervisor.send(tromboneAssembly, Running)
//
//    val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//    fakeClient.send(tromboneAssembly, Submit(datum))
//
//    // This first one is the accept/verification
//    var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//    acceptedMsg.overall shouldBe Accepted
//    // Second one is completion of the executed datum
//    var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//    completeMsg.overall shouldBe AllCompleted
//
//    val testEl = 150.0
//    val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl), followSC(false))
//
//    // Send the setElevation
//    fakeClient.send(tromboneAssembly, Submit(sca))
//
//    // This first one is the accept/verification
//    acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//    acceptedMsg.overall shouldBe Accepted
//    //logger.info(s"AcceptedMsg: $acceptedMsg")
//
//    // Second one is completion of the executed ones
//    completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
//    logger.info(s"completeMsg: $completeMsg")
//    completeMsg.overall shouldBe AllCompleted
//
//    // Now send some events
//    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
//    val tcsRtc = eventService
//
//    val testFE = 10.0
//    // Publish a single focus error. This will generate a published event
//    tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))
//
//    val testZenithAngles = 0.0 to 40.0 by 5.0
//    // These are fake messages for the FollowActor that will be sent to simulate the TCS
//    val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))
//
//    // This should result in the length of tcsEvents being published
//    tcsEvents.map { f =>
//      logger.info(s"Publish: $f")
//      tcsRtc.publish(f)
//    }
//
//    expectNoMsg(10.seconds)
//
//    val monitor = TestProbe()
//    monitor.watch(tromboneAssembly)
//    system.stop(tromboneAssembly)
//    monitor.expectTerminated(tromboneAssembly)
//
//  }
//
}
