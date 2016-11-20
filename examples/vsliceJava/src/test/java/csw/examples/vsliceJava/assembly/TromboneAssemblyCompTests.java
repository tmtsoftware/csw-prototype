package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import csw.services.ccs.AssemblyController.Submit;
import csw.services.ccs.CommandStatus.CommandResult;
import csw.services.loc.LocationService;
import csw.services.pkg.SupervisorExternal.SubscribeLifecycleCallback;
import csw.util.config.Configurations;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Configurations.SetupConfigArg;
import javacsw.services.pkg.JSupervisor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static csw.services.pkg.SupervisorExternal.LifecycleStateChanged;
import static javacsw.services.ccs.JCommandStatus.*;
import static javacsw.services.pkg.JSupervisor.LifecycleInitialized;
import static javacsw.services.pkg.JSupervisor.LifecycleRunning;
import static junit.framework.TestCase.assertEquals;

@SuppressWarnings({"WeakerAccess", "OptionalUsedAsFieldOrParameterType", "MismatchedReadAndWriteOfArray"})
public class TromboneAssemblyCompTests extends JavaTestKit {
  private static ActorSystem system;
  private static LoggingAdapter logger;

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public TromboneAssemblyCompTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  ActorRef newTrombone() {
    return JSupervisor.create(assemblyContext.info);
  }

  // --- comp tests ---

  @Test
  public void test1() {
    // should just startup
    ActorRef tla = newTrombone();
    TestProbe fakeSequencer = new TestProbe(system);

    tla.tell(new SubscribeLifecycleCallback(fakeSequencer.ref()), self());
    fakeSequencer.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeSequencer.expectMsg(new LifecycleStateChanged(LifecycleRunning));

    fakeSequencer.expectNoMsg(duration("3 seconds")); // wait for connections
  }

  @Test
  public void test2() {
    // should allow a datum
    ActorRef tla = newTrombone();
    TestProbe fakeSequencer = new TestProbe(system);

    tla.tell(new SubscribeLifecycleCallback(fakeSequencer.ref()), self());
    fakeSequencer.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeSequencer.expectMsg(new LifecycleStateChanged(LifecycleRunning));

    fakeSequencer.expectNoMsg(duration("3 seconds")); // wait for connections

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()), new SetupConfig(assemblyContext.datumCK.prefix()));

    fakeSequencer.send(tla, new Submit(sca));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeSequencer.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(acceptedMsg.overall(), Accepted);

    CommandResult completeMsg = fakeSequencer.expectMsgClass(duration("3 seconds"), CommandResult.class);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().status(0), Completed);
    // Wait a bit to see if there is any spurious messages
    fakeSequencer.expectNoMsg(duration("250 milli"));
    logger.info("Msg: " + completeMsg);
  }

  @Test
  public void test3() {
    // should allow a datum then a set of positions as separate sca
    ActorRef tla = newTrombone();
    TestProbe fakeSequencer = new TestProbe(system);

    tla.tell(new SubscribeLifecycleCallback(fakeSequencer.ref()), self());
    fakeSequencer.expectMsg(new LifecycleStateChanged(LifecycleInitialized));
    fakeSequencer.expectMsg(new LifecycleStateChanged(LifecycleRunning));

    fakeSequencer.expectNoMsg(duration("3 seconds")); // wait for connections

    SetupConfigArg datum = Configurations.createSetupConfigArg("testobsId",
      new SetupConfig(assemblyContext.initCK.prefix()), new SetupConfig(assemblyContext.datumCK.prefix()));

    fakeSequencer.send(tla, new Submit(datum));

    // This first one is the accept/verification
    CommandResult acceptedMsg = fakeSequencer.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg1: " + acceptedMsg);
    assertEquals(acceptedMsg.overall(), Accepted);

    CommandResult completeMsg = fakeSequencer.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg2: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
//------

    // This will send a config arg with 10 position commands
    int[] testRangeDistance = new int[]{90, 100, 110, 120, 130, 140, 150, 160, 170, 180};
    List<SetupConfig> positionConfigs = Arrays.stream(testRangeDistance).mapToObj(f -> assemblyContext.positionSC(f))
      .collect(Collectors.toList());

    SetupConfigArg sca = Configurations.createSetupConfigArg("testobsId", positionConfigs);
    fakeSequencer.send(tla, new Submit(sca));

    // This first one is the accept/verification
    acceptedMsg = fakeSequencer.expectMsgClass(duration("3 seconds"), CommandResult.class);
    logger.info("msg1: " + acceptedMsg);
    assertEquals(acceptedMsg.overall(), Accepted);

    // Second one is completion of the executed ones - give this some extra time to complete
    completeMsg = fakeSequencer.expectMsgClass(duration("10 seconds"), CommandResult.class);
    logger.info("msg2: " + completeMsg);
    assertEquals(completeMsg.overall(), AllCompleted);
    assertEquals(completeMsg.details().results().size(), sca.configs().size());
  }
}
