package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.util.Timeout;
import csw.services.loc.LocationService;
import csw.util.config.BooleanItem;
import javacsw.services.events.IEventService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static csw.examples.vsliceJava.assembly.AssemblyContext.*;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.testFocusErrors;
import static csw.examples.vsliceJava.assembly.AssemblyTestData.testZenithAngles;
import static csw.examples.vsliceJava.assembly.FollowActor.StopFollowing;
import static csw.examples.vsliceJava.assembly.FollowActor.UpdatedEventData;
import static csw.util.config.Events.SystemEvent;
import static javacsw.util.config.JItems.jvalue;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static csw.examples.vsliceJava.assembly.TromboneEventSubscriber.UpdateNssInUse;

/**
 * Diag Pubisher Tests
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "FieldCanBeLocal", "WeakerAccess"})
public class EventSubscriberTests extends JavaTestKit {

  private static ActorSystem system;
  private static LoggingAdapter logger;

  private static Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));

  private static AssemblyContext assemblyContext = AssemblyTestData.TestAssemblyContext;

  private static IEventService eventService;

  // This def helps to make the test code look more like normal production code, where self() is defined in an actor class
  ActorRef self() {
    return getTestActor();
  }

  public EventSubscriberTests() {
    super(system);
  }

  @BeforeClass
  public static void setup() throws Exception {
    LocationService.initInterface();
    system = ActorSystem.create();
    logger = Logging.getLogger(system, system);

    eventService = IEventService.getEventService(IEventService.defaultName, system, timeout)
      .get(5, TimeUnit.SECONDS);
    logger.info("Got Event Service!");
  }

  @AfterClass
  public static void teardown() {
    JavaTestKit.shutdownActorSystem(system);
    system = null;
  }

  TestActorRef<TromboneEventSubscriber> newTestEventSubscriber(BooleanItem nssInUseIn, Optional<ActorRef> followActor, IEventService eventService) {
    Props props = TromboneEventSubscriber.props(assemblyContext, nssInUseIn, followActor, eventService);
    return TestActorRef.create(system, props);
  }

  ActorRef newEventSubscriber(BooleanItem nssInUse, Optional<ActorRef> followActor, IEventService eventService) {
    Props props = TromboneEventSubscriber.props(assemblyContext, nssInUse, followActor, eventService);
    return system.actorOf(props);
  }

  // --- basic event subscriber tests ---

  @Test
  public void test1() {
    // should be created with no issues
    TestProbe fakeFollowActor = new TestProbe(system);

    TestActorRef<TromboneEventSubscriber> es = newTestEventSubscriber(assemblyContext.setNssInUse(false),
      Optional.of(fakeFollowActor.ref()), eventService);

    assertEquals(es.underlyingActor().nssZenithAngle, za(0.0));
    assertEquals(es.underlyingActor().initialFocusError, fe(0.0));
    assertEquals(es.underlyingActor().initialZenithAngle, za(0.0));
    assertEquals(es.underlyingActor().nssInUseGlobal, assemblyContext.setNssInUse(false));

    es.tell(new StopFollowing(), self());
    fakeFollowActor.expectNoMsg(duration("20 milli"));
  }

  // --- tests for proper operation ---

  @Test
  public void test2() throws InterruptedException, ExecutionException, TimeoutException {
    // should make one event for an fe publish nssInUse
    TestProbe fakeFollowActor = new TestProbe(system);

    ActorRef es = newEventSubscriber(assemblyContext.setNssInUse(true), Optional.of(fakeFollowActor.ref()), eventService);

    // first test that events are created for published focus error events
    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
    IEventService tcsRtc = eventService;

    // Default ZA is 0.0
    double testFE = 10.0;
    // Publish a single focus error. This will generate a published event
    tcsRtc.publish(new SystemEvent(focusErrorPrefix).add(fe(testFE))).get(2, TimeUnit.SECONDS);

    UpdatedEventData msg = fakeFollowActor.expectMsgClass(UpdatedEventData.class);

    assertEquals(msg.focusError, fe(testFE));
    // 0.0 is the default value as well as nssZenithAngle
    assertEquals(msg.zenithAngle, za(0.0));

    system.stop(es);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));
  }


  @Test
  public void test3() {
    // should make several events for an fe list publish with nssInUse but no ZA
    TestProbe fakeFollowActor = new TestProbe(system);

    ActorRef es = newEventSubscriber(assemblyContext.setNssInUse(true), Optional.of(fakeFollowActor.ref()), eventService);

    // first test that events are created for published focus error events
    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
    IEventService tcsRtc = eventService;

    // Publish a single focus error. This will generate a published event
    List<SystemEvent> feEvents = testFocusErrors.stream().map(f -> new SystemEvent(focusErrorPrefix).add(fe(f)))
      .collect(Collectors.toList());

    // These are fake messages for the FollowActor that will be sent to simulate the TCS
    List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    feEvents.forEach(tcsRtc::publish);

    assertEquals(fakeFollowActor.receiveN(feEvents.size(), timeout.duration()).size(), feEvents.size());

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));

    // Should get no tcsEvents because not following
    tcsEvents.forEach(tcsRtc::publish);

    system.stop(es);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));
  }

  @Test
  public void test4() {
    // now enable follow should make several events for za and fe list publish nssNotInUse
    TestProbe fakeFollowActor = new TestProbe(system);

    ActorRef es = newEventSubscriber(assemblyContext.setNssInUse(false), Optional.of(fakeFollowActor.ref()), eventService);

    // first test that events are created for published focus error events
    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
    IEventService tcsRtc = eventService;

    // Publish a single focus error. This will generate a published event
    List<SystemEvent> feEvents = testFocusErrors.stream().map(f -> new SystemEvent(focusErrorPrefix).add(fe(f)))
      .collect(Collectors.toList());

    // These are fake messages for the FollowActor that will be sent to simulate the TCS
    List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    feEvents.forEach(tcsRtc::publish);

    // XXX Note: The Scala version of this test uses TestKit.receiveN, which returns a Scala Seq, so we need to convert here
    // (I didn't find a Java API for this)
    List<UpdatedEventData> feEventMsgs =
      scala.collection.JavaConversions.asJavaCollection(fakeFollowActor.receiveN(feEvents.size()))
        .stream().map(f -> (UpdatedEventData) f)
        .collect(Collectors.toList());

    assertEquals(feEventMsgs.size(), feEvents.size());
    List<Double> fevals = feEventMsgs.stream().map(f -> jvalue(f.focusError))
      .collect(Collectors.toList());

    // Should equal test vals
    assertEquals(fevals, testFocusErrors);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));

    // Should get no tcsEvents because not following
    tcsEvents.forEach(tcsRtc::publish);

    // Should get several and the zenith angles should match since nssInUse was false
    List<UpdatedEventData> msgs =
      scala.collection.JavaConversions.asJavaCollection(fakeFollowActor.receiveN(tcsEvents.size()))
        .stream().map(f -> (UpdatedEventData) f)
        .collect(Collectors.toList());

    List<Double> zavals = msgs.stream().map(f -> jvalue(f.zenithAngle))
      .collect(Collectors.toList());

    // Should equal input za
    assertEquals(zavals, testZenithAngles);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));

    // Now turn it off
    es.tell(new StopFollowing(), self());
    // Give a little wait for the usubscribe to kick in before the publish events
    fakeFollowActor.expectNoMsg(duration("20 milli"));

    // Should get no tcsEvents because not following
    tcsEvents.forEach(tcsRtc::publish);

    system.stop(es);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));
  }

  @Test
  public void test5() {
    // alter nssInUse to see switch to nssZenithAngles
    TestProbe fakeFollowActor = new TestProbe(system);

    // Create with nssNotInuse so we get za events
    ActorRef es = newEventSubscriber(assemblyContext.setNssInUse(false), Optional.of(fakeFollowActor.ref()), eventService);

    // first test that events are created for published focus error events
    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
    IEventService tcsRtc = eventService;

    // Publish a single focus error. This will generate a published event
    List<SystemEvent> feEvents = testFocusErrors.stream().map(f -> new SystemEvent(focusErrorPrefix).add(fe(f)))
      .collect(Collectors.toList());

    // These are fake messages for the FollowActor that will be sent to simulate the TCS
    List<SystemEvent> tcsEvents = testZenithAngles.stream().map(f -> new SystemEvent(zaConfigKey.prefix()).add(za(f)))
      .collect(Collectors.toList());

    double testZA = 45.0;
    tcsRtc.publish(new SystemEvent(zenithAnglePrefix).add(za(testZA)));
    UpdatedEventData one = fakeFollowActor.expectMsgClass(UpdatedEventData.class);
    assertEquals(jvalue(one.zenithAngle), testZA);

    // Now follow with nssInUse and send feEvents, should have 0.0 as ZA
    es.tell(new UpdateNssInUse(assemblyContext.setNssInUse(true)), self());

    // Now send the events
    feEvents.forEach(tcsRtc::publish);

    List<UpdatedEventData> msgs2 =
      scala.collection.JavaConversions.asJavaCollection(fakeFollowActor.receiveN(feEvents.size()))
        .stream().map(f -> (UpdatedEventData) f)
        .collect(Collectors.toList());

    // Each zenith angle with the message should be 0.0 now, not 45.0
    List<Double> zavals = msgs2.stream().map(f -> jvalue(f.zenithAngle))
      .collect(Collectors.toList());

    assertTrue(zavals.stream().filter(f -> f != 0.0).collect(Collectors.toList()).isEmpty());

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));

    // Should get no tcsEvents because nssInUse = true
    tcsEvents.forEach(tcsRtc::publish);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));

    // Now turn it off
    es.tell(new StopFollowing(), self());

    // Give a little wait for the usubscribe to kick in before the publish events
    fakeFollowActor.expectNoMsg(duration("20 milli"));

    // Should get no tcsEvents because not following
    tcsEvents.forEach(tcsRtc::publish);

    // No more messages please
    fakeFollowActor.expectNoMsg(duration("100 milli"));
  }
}
