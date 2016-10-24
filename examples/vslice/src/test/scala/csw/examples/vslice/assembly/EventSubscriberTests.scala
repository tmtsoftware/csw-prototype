package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.examples.vslice.assembly.FollowActor.{StopFollowing, UpdatedEventData}
import csw.examples.vslice.assembly.TromboneEventSubscriber.UpdateNssInUse
import csw.services.events.{EventService, EventServiceSettings}
import csw.services.loc.LocationService
import csw.util.config.BooleanItem
import csw.util.config.Events.SystemEvent
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.duration._

object EventSubscriberTests {
  LocationService.initInterface()
  val system = ActorSystem("EventSubscriberTests")
}

/**
 * TMT Source Code: 9/17/16.
 */
class EventSubscriberTests extends TestKit(EventSubscriberTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  // This is used for testing and insertion into components for testing
  def eventConnection: EventService = EventService(testEventServiceSettings)

  var testEventService: Option[EventService] = None
  override def beforeAll() = {
    testEventService = Some(eventConnection)
  }

  override def afterAll = {
    TestKit.shutdownActorSystem(system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  import assemblyContext._

  val testEventServiceSettings = EventServiceSettings("localhost", 7777)

  def newTestEventSubscriber(nssInUseIn: BooleanItem, followActor: Option[ActorRef], eventService: Option[EventService]): TestActorRef[TromboneEventSubscriber] = {
    val props = TromboneEventSubscriber.props(assemblyContext, nssInUseIn, followActor, eventService)
    TestActorRef(props)
  }

  def newEventSubscriber(nssInUse: BooleanItem, followActor: Option[ActorRef], eventService: Option[EventService]): ActorRef = {
    val props = TromboneEventSubscriber.props(assemblyContext, nssInUse, followActor, eventService)
    system.actorOf(props)
  }

  describe("basic event subscriber tests") {

    it("should be created with no issues") {
      val fakeFollowActor = TestProbe()

      val es = newTestEventSubscriber(setNssInUse(false), Some(fakeFollowActor.ref), testEventService)

      es.underlyingActor.nssZenithAngle should equal(za(0.0))
      es.underlyingActor.initialFocusError should equal(fe(0.0))
      es.underlyingActor.initialZenithAngle should equal(za(0.0))
      es.underlyingActor.nssInUseGlobal shouldBe setNssInUse(false)

      es ! StopFollowing
      fakeFollowActor.expectNoMsg(20.milli)
    }
  }

  describe("tests for proper operation") {
    import AssemblyTestData._

    it("should make one event for an fe publish nssInUse") {
      val fakeFollowActor = TestProbe()

      val es = newEventSubscriber(setNssInUse(true), Some(fakeFollowActor.ref), testEventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Default ZA is 0.0
      val testFE = 10.0
      // Publish a single focus error. This will generate a published event
      tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))

      val msg = fakeFollowActor.expectMsgClass(classOf[UpdatedEventData])

      msg.focusError should equal(fe(testFE))
      // 0.0 is the default value as well as nssZenithAngle
      msg.zenithAngle should equal(za(0.0))

      system.stop(es)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

    }

    it("should make several events for an fe list publish with nssInUse but no ZA") {
      val fakeFollowActor = TestProbe()

      val es = newEventSubscriber(setNssInUse(true), Some(fakeFollowActor.ref), testEventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Publish a single focus error. This will generate a published event
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      feEvents.map(f => tcsRtc.publish(f))

      val msgs2 = fakeFollowActor.receiveN(feEvents.size)
      msgs2.size shouldBe feEvents.size

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Should get no tcsEvents because not following
      tcsEvents.map(f => tcsRtc.publish(f))

      system.stop(es)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)
    }

    it("now enable follow should make several events for za and fe list publish nssNotInUse") {
      val fakeFollowActor = TestProbe()

      val es = newEventSubscriber(setNssInUse(false), Some(fakeFollowActor.ref), testEventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Publish a single focus error. This will generate a published event
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      feEvents.map(f => tcsRtc.publish(f))

      val feEventMsgs: Vector[UpdatedEventData] = fakeFollowActor.receiveN(feEvents.size).asInstanceOf[Vector[UpdatedEventData]]
      feEventMsgs.size should equal(feEvents.size)
      val fevals = feEventMsgs.map(f => f.focusError.head)
      // Should equal test vals
      fevals should equal(testFocusErrors)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Should get no tcsEvents because not following
      tcsEvents.map(f => tcsRtc.publish(f))

      // Should get several and the zenith angles should match since nssInUse was false
      val msgs: Vector[UpdatedEventData] = fakeFollowActor.receiveN(tcsEvents.size).asInstanceOf[Vector[UpdatedEventData]]
      val zavals = msgs.map(f => f.zenithAngle.head)
      // Should equal input za
      zavals should equal(testZenithAngles)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Now turn it off
      es ! StopFollowing
      // Give a little wait for the usubscribe to kick in before the publish events
      fakeFollowActor.expectNoMsg(20.milli)

      // Should get no tcsEvents because not following
      tcsEvents.map(f => tcsRtc.publish(f))

      system.stop(es)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)
    }

    it("alter nssInUse to see switch to nssZenithAngles") {
      val fakeFollowActor = TestProbe()

      // Create with nssNotInuse so we get za events
      val es = newEventSubscriber(setNssInUse(false), Some(fakeFollowActor.ref), testEventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = EventService(testEventServiceSettings)

      // Publish a single focus error. This will generate a published event
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      // Start following with nssInUse to send one event with a non zero zenith angle
      //es ! UpdateNssInUse(nssInUse)

      val testZA = 45.0
      tcsRtc.publish(SystemEvent(zenithAnglePrefix).add(za(testZA)))
      val one: UpdatedEventData = fakeFollowActor.expectMsgClass(classOf[UpdatedEventData])
      one.zenithAngle.head shouldBe testZA

      // Now follow with nssInUse and send feEvents, should have 0.0 as ZA
      es ! UpdateNssInUse(setNssInUse(true))

      // Now send the events
      feEvents.map(f => tcsRtc.publish(f))
      val msgs2: Vector[UpdatedEventData] = fakeFollowActor.receiveN(feEvents.size).asInstanceOf[Vector[UpdatedEventData]]
      // Each zenith angle with the message should be 0.0 now, not 45.0
      val zavals = msgs2.map(f => f.zenithAngle.head)
      zavals.filter(_ != 0.0) shouldBe empty

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Should get no tcsEvents because nssInUse = true
      tcsEvents.map(f => tcsRtc.publish(f))

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Now turn it off
      es ! StopFollowing

      // Give a little wait for the usubscribe to kick in before the publish events
      fakeFollowActor.expectNoMsg(20.milli)

      // Should get no tcsEvents because not following
      tcsEvents.map(f => tcsRtc.publish(f))

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)
    }
  }

}
