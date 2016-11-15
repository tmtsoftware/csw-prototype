package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.ccs.CommandStatus2;
import csw.services.ccs.SequentialExecutor;
import csw.services.ccs.Validation;
import csw.services.loc.LocationService;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor3;
import csw.util.config.Configurations;
import javacsw.services.events.IEventService;
import javacsw.services.pkg.JComponent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static csw.examples.vsliceJava.assembly.TromboneStateActor.*;
import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisUpdate;
import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.GetAxisUpdateNow;
import static csw.services.ccs.CommandStatus2.CommandResult;
import static csw.services.ccs.CommandStatus2.NoLongerValid;
import static csw.services.ccs.SequentialExecutor.StartTheSequence;
import static csw.services.pkg.SupervisorExternal.LifecycleStateChanged;
import static csw.services.pkg.SupervisorExternal.SubscribeLifecycleCallback;
import static csw.util.config.Configurations.SetupConfig;
import static csw.util.config.Configurations.SetupConfigArg;
import static javacsw.services.ccs.JCommandStatus2.*;
import static javacsw.services.ccs.JSequentialExecutor.ExecuteOne;
import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.services.pkg.JComponent.DoNotRegister;
import static javacsw.services.pkg.JSupervisor3.LifecycleInitialized;
import static javacsw.services.pkg.JSupervisor3.LifecycleRunning;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

@SuppressWarnings({"WeakerAccess", "OptionalUsedAsFieldOrParameterType", "MismatchedReadAndWriteOfArray"})
public class CommandHandlerTests extends JavaTestKit {
  private static ActorSystem system;
  private static LoggingAdapter logger;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public CommandHandlerTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create("TromboneAssemblyCommandHandlerTests");
    logger = Logging.getLogger(system, system);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  static final AssemblyContext ac = AssemblyTestData.TestAssemblyContext;

  void setupState(TromboneState ts) {
    // These times are important to allow time for test actors to get and process the state updates when running tests
    expectNoMsg(FiniteDuration.apply(200, TimeUnit.MILLISECONDS));
    system.eventStream().publish(ts);
    // This is here to allow the destination to run and set its state
    expectNoMsg(FiniteDuration.apply(200, TimeUnit.MILLISECONDS));
  }

  ActorRef startHCD() {
    Component.HcdInfo testInfo = JComponent.hcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Collections.singleton(AkkaType), FiniteDuration.create(1, TimeUnit.SECONDS)
    );

    return Supervisor3.apply(testInfo);
  }

  ActorRef newCommandHandler(ActorRef tromboneHCD, Optional<ActorRef> allEventPublisher) {
    return system.actorOf(TromboneCommandHandler.props(ac, Optional.of(tromboneHCD), allEventPublisher));
  }

  @Test
  public void shouldAllowRunningDatumDirectlyToCommandHandler() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));

//    ActorRef tsa = system.actorOf(TromboneStateActor.props());

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)));

    Configurations.SetupConfig sc = new SetupConfig(ac.datumCK.prefix());

    ch.tell(ExecuteOne(sc, Optional.of(fakeAssembly.ref())), self());

    CommandStatus2.CommandStatus2 msg = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS),
      CommandStatus2.CommandStatus2.class);
    assertEquals(msg, Completed);
    //info("Final: " + msg)

    // Demonstrate error
    ch.tell(new TromboneState(cmdItem(cmdUninitialized), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)), self());
    ch.tell(ExecuteOne(sc, Optional.of(fakeAssembly.ref())), self());

    CommandStatus2.CommandStatus2 errMsg = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS),
      CommandStatus2.CommandStatus2.class);

    assertTrue(errMsg instanceof NoLongerValid);

    TestProbe monitor = new TestProbe(system);
    monitor.watch(ch);
    system.stop(ch);
//    system.stop(tsa);
    monitor.expectTerminated(ch, FiniteDuration.create(1, TimeUnit.SECONDS));
  }

// XXX TODO: Was added after Java port
//  it("datum should handle change in HCD") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    //val tsa = system.actorOf(TromboneStateActor.props)
//
//    // Start with good HCD
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)))
//
//    val sc = SetupConfig(ac.datumCK)
//
//    ch ! ExecuteOne(sc, Some(fakeAssembly.ref))
//
//    val msg: CommandStatus2 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandStatus2])
//    msg shouldBe Completed
//    //info("Final: " + msg
//
//    val unresolvedHCD = Unresolved(AkkaConnection(ac.hcdComponentId))
//    ch ! unresolvedHCD
//
//    ch ! ExecuteOne(sc, Some(fakeAssembly.ref))
//
//    val errMsg: CommandStatus2 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandStatus2])
//    errMsg shouldBe a[NoLongerValid]
//    errMsg.asInstanceOf[NoLongerValid].issue shouldBe a[RequiredHCDUnavailableIssue]
//
//    val resolvedHCD = ResolvedAkkaLocation(AkkaConnection(ac.hcdComponentId), new URI("http://help"), "", Some(tromboneHCD))
//    ch ! resolvedHCD
//
//    ch ! ExecuteOne(sc, Some(fakeAssembly.ref))
//    val msg2: CommandStatus2 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandStatus2])
//    msg2 shouldBe Completed
//
//    val monitor = TestProbe()
//    monitor.watch(ch)
//    ch ! PoisonPill
//    monitor.expectTerminated(ch)
//  }




  @Test
  public void shouldAllowRunningDatumThroughSequentialExecutor() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)));

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", new SetupConfig(ac.datumCK.prefix()));

    ActorRef se = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));

    se.tell(new SequentialExecutor.StartTheSequence(ch), self());

    CommandResult msg = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    //info("Final: " + msg)
    assertEquals(msg.overall(), AllCompleted);
    assertEquals(msg.details().results().size(), 1);

    // Demonstrate error
    ch.tell(new TromboneState(cmdItem(cmdUninitialized), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)), self());

    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se2.tell(new StartTheSequence(ch), self());


    CommandResult errMsg = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    assertEquals(errMsg.overall(), Incomplete);
    CommandStatus2.CommandStatus2 e1 = errMsg.details().getResults().get(0).first();
    assertTrue(e1 instanceof NoLongerValid);
    assertTrue(((NoLongerValid)e1).issue() instanceof Validation.WrongInternalStateIssue);

    //info("Final: " + errMsg)

    TestProbe monitor = new TestProbe(system);
    monitor.watch(ch);
    monitor.watch(tromboneHCD);
    system.stop(ch); // ch ! PoisonPill
    monitor.expectTerminated(ch, FiniteDuration.create(1, TimeUnit.SECONDS));
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender()); //tromboneHCD ! PoisonPill
    monitor.expectTerminated(tromboneHCD, FiniteDuration.create(4, TimeUnit.SECONDS));
  }

  @Test
  public void shouldAllowRunningMove() {
    ActorRef tromboneHCD = startHCD();

    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    //expectNoMsg(100.milli)

    double testPosition = 90.0;
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.moveSC(testPosition));

    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se2.tell(new StartTheSequence(ch), self());

    fakeAssembly.expectMsgClass(FiniteDuration.create(35, TimeUnit.SECONDS), CommandResult.class);
    int finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testPosition);

    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, finalPos);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    system.stop(se2);
  }

  @Test
  public void shouldAllowRunningAMoveWithoutSequence() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    //expectNoMsg(100.milli)

    double testPosition = 90.0;
    ch.tell(ExecuteOne(ac.moveSC(testPosition), Optional.of(fakeAssembly.ref())), self());

    fakeAssembly.expectMsgClass(FiniteDuration.create(35, TimeUnit.SECONDS), CommandStatus2.CommandStatus2.class);
    int finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testPosition);

    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, finalPos);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowTwoMoves() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    double pos1 = 86.0;
    double pos2 = 150.1;

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.moveSC(pos1), ac.moveSC(pos2));
    System.out.println("SCA: " + sca);

    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se2.tell(new StartTheSequence(ch), self());

    int finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, pos2);

    CommandResult msg = fakeAssembly.expectMsgClass(FiniteDuration.create(35, TimeUnit.SECONDS), CommandResult.class);
    logger.info("result: " + msg);

    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, finalPos);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowAMoveWithAStop() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    double pos1 = 150.1;

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.moveSC(pos1));

    ActorRef se = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se.tell(new StartTheSequence(ch), self());

//    Configurations.createSetupConfigArg("testobsId", new SetupConfig(ac.stopCK.prefix()));
    try {
      Thread.sleep(20); // This is an arbitrary time to get things going before sending stop
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // This won't work
    //val se2 = system.actorOf(SequentialExecutor.props(sca2, Optional.of(fakeAssembly.ref)))
    //se2 ! StartTheSequence(ch)

    // This will also work
    //  se ! StopCurrentCommand
    ch.tell(new SetupConfig(ac.stopCK.prefix()), self());

    CommandResult msg = fakeAssembly.expectMsgClass(FiniteDuration.create(35, TimeUnit.SECONDS), CommandResult.class);
    assertEquals(msg.overall(), Incomplete);
    assertEquals(msg.details().status(0), Cancelled);
    logger.info("result: " + msg);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowASinglePositionCommand() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    double testRangeDistance = 94.0;
    SetupConfig positionConfig = ac.positionSC(testRangeDistance);
    logger.info("Position: " + positionConfig);
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", positionConfig);

    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se2.tell(new StartTheSequence(ch), self());

    fakeAssembly.expectMsgClass(FiniteDuration.create(5, TimeUnit.SECONDS), CommandResult.class);
    int finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testRangeDistance);

    // Use the engineering GetAxisUpdate to get the current encoder
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, finalPos);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowASetOfPositionsForTheFunOfIt() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    // This will send a config arg with 10 position commands
    int[] testRangeDistance = new int[]{90, 100, 110, 120, 130, 140, 150, 160, 170, 180}; // 90 to 180 by 10

    SetupConfig[] positionConfigs = Arrays.stream(testRangeDistance).mapToObj(ac::positionSC)
      .toArray(SetupConfig[]::new);

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", positionConfigs);

    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se2.tell(new StartTheSequence(ch), self());

    CommandResult msg = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    //info("Final: " + msg)

    // Test
    int finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testRangeDistance[testRangeDistance.length-1]);
    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, finalPos);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowRunningASetElevationWithoutSequence() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    double testEl = 150.0;
    ch.tell(ExecuteOne(ac.setElevationSC(testEl), Optional.of(fakeAssembly.ref())), self());

    fakeAssembly.expectMsgClass(FiniteDuration.create(5, TimeUnit.SECONDS), CommandStatus2.CommandStatus2.class);
    int finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testEl);

    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, finalPos);
    logger.info("Upd: " + upd);

    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldGetErrorForSetAngleWhenNotFollowing() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());

    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.setAngleSC(22.0));

    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));

    se2.tell(new StartTheSequence(ch), self());

    CommandResult errMsg = fakeAssembly.expectMsgClass(FiniteDuration.create(35, TimeUnit.SECONDS), CommandResult.class);
    assertEquals(errMsg.overall(), Incomplete);
    assertTrue(errMsg.details().getResults().get(0).first() instanceof NoLongerValid);

    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowFollowAndAStop() {

    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());
    LocationService.ResolvedTcpLocation evLocation = new LocationService.ResolvedTcpLocation(
      IEventService.eventServiceConnection(), "localhost", 7777);
    ch.tell(evLocation, self());

    // set the state so the command succeeds
    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(false)));

    //fakeAssembly.expectNoMsg(30.milli)
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.followSC(false), new SetupConfig(ac.stopCK.prefix()));
    ActorRef se = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se.tell(new StartTheSequence(ch), self());

    CommandResult msg2 = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    logger.info("Msg: " + msg2);
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowFollowWithTwoSetAnglesAndStop() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());
    LocationService.ResolvedTcpLocation evLocation = new LocationService.ResolvedTcpLocation(
      IEventService.eventServiceConnection(), "localhost", 7777);
    ch.tell(evLocation, self());

    // I'm sending this event to the follower so I know its state so I can check the final result
    // to see that it moves the stage to the right place when sending a new elevation
    double testFocusError = 0.0;
    double testElevation = 100.0;
    double initialZenithAngle = 0.0;

    // set the state so the command succeeds - NOTE: Setting sodiumItem true here
    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)));

    //fakeAssembly.expectNoMsg(30.milli)
    double totalRangeDistance = Algorithms.focusZenithAngleToRangeDistance(ac.calculationConfig, testElevation, testFocusError, initialZenithAngle);
    double stagePosition = Algorithms.rangeDistanceToStagePosition(totalRangeDistance);
    int expectedEncoderValue = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition);
    logger.info("Expected for setElevation: " + expectedEncoderValue);

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.setElevationSC(testElevation));
    ActorRef se2 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se2.tell(new StartTheSequence(ch), self());

    CommandResult msg1 = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    logger.info("Msg: " + msg1);

    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    assertEquals(upd.current, expectedEncoderValue);

//    fakeAssembly.expectNoMsg(2.seconds)

    // This sets up the follow command to put assembly into follow mode
    sca = Configurations.createSetupConfigArg("testobsId", ac.followSC(false));
    ActorRef se = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se.tell(new StartTheSequence(ch), self());
    CommandResult msg2 = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    logger.info("Msg2: " + msg2);

    double testZenithAngle = 30.0;
    sca = Configurations.createSetupConfigArg("testobsId", ac.setAngleSC(testZenithAngle));
    ActorRef se3 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se3.tell(new StartTheSequence(ch), self());

    CommandResult msg3 = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    logger.info("Msg3: " + msg3);

    totalRangeDistance = Algorithms.focusZenithAngleToRangeDistance(ac.calculationConfig, testElevation, testFocusError, testZenithAngle);
    stagePosition = Algorithms.rangeDistanceToStagePosition(totalRangeDistance);
    expectedEncoderValue = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition);
    logger.info("Expected for setAngle: " + expectedEncoderValue);

    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    logger.info("Upd2: " + upd);
    assertEquals(upd.current, expectedEncoderValue);

    sca = Configurations.createSetupConfigArg("testobsId", new SetupConfig(ac.stopCK.prefix()));
    ActorRef se4 = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se4.tell(new StartTheSequence(ch), self());

    CommandResult msg5 = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    logger.info("Msg: " + msg5);
    fakeAssembly.expectNoMsg(FiniteDuration.apply(1, TimeUnit.SECONDS));
    system.stop(ch);
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
  }

  @Test
  public void shouldAllowOneArgWithSetElevationFollowSetAngleAndStopAsASingleSequence() {
    ActorRef tromboneHCD = startHCD();
    TestProbe fakeAssembly = new TestProbe(system);

    // The following is to synchronize the test with the HCD entering Running state
    // This is boiler plate for setting up an HCD for testing
    tromboneHCD.tell(new SubscribeLifecycleCallback(fakeAssembly.ref()), self());
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeAssembly.expectMsg(new LifecycleStateChanged(LifecycleRunning));
    //info("Running")

    ActorRef ch = newCommandHandler(tromboneHCD, Optional.empty());
    LocationService.ResolvedTcpLocation evLocation = new LocationService.ResolvedTcpLocation(
      IEventService.eventServiceConnection(), "localhost", 7777);
    ch.tell(evLocation, self());

    // I'm sending this event to the follower so I know its state so I can check the final result
    // to see that it moves the stage to the right place when sending a new elevation
    double testFocusError = 0.0;
    double testElevation = 100.0;
    double testZenithAngle = 30.0;

    // set the state so the command succeeds
    setupState(new TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(false)));

    //fakeAssembly.expectNoMsg(30.milli)
    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", ac.setElevationSC(testElevation), ac.followSC(false), ac.setAngleSC(testZenithAngle),
      new SetupConfig(ac.stopCK.prefix()));
    ActorRef se = system.actorOf(SequentialExecutor.props(sca, Optional.of(fakeAssembly.ref())));
    se.tell(new StartTheSequence(ch), self());

    CommandResult msg = fakeAssembly.expectMsgClass(FiniteDuration.create(10, TimeUnit.SECONDS), CommandResult.class);
    logger.info(">>>>>>>Msg: " + msg);

    fakeAssembly.expectNoMsg(FiniteDuration.apply(2, TimeUnit.SECONDS));

    double totalRangeDistance = Algorithms.focusZenithAngleToRangeDistance(ac.calculationConfig, testElevation, testFocusError, testZenithAngle);
    double stagePosition = Algorithms.rangeDistanceToStagePosition(totalRangeDistance);
    int expectedEncoderValue = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition);
    logger.info("Expected for setAngle: " + expectedEncoderValue);

    // Use the engineering GetAxisUpdate to get the current encoder for checking
    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow);
    AxisUpdate upd = fakeAssembly.expectMsgClass(AxisUpdate.class);
    logger.info("Upd2: " + upd);

    // Cleanup
    tromboneHCD.tell(PoisonPill.getInstance(), ActorRef.noSender());
    TestProbe monitor = new TestProbe(system);
    monitor.watch(ch);
    system.stop(ch);
    monitor.expectTerminated(ch, FiniteDuration.apply(1, TimeUnit.SECONDS));
  }

}
