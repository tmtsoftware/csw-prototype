package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.examples.vslice.assembly.FollowActor.{StopFollowing, UpdatedEventData}
import csw.examples.vslice.assembly.TromboneEventSubscriber.UpdateNssInUse
import csw.services.events.EventService
import csw.services.loc.LocationService
import csw.util.config.BooleanItem
import csw.util.config.Events.SystemEvent
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.Await
import scala.concurrent.duration._

object EventSubscriberTests {
  LocationService.initInterface()
  val sys = ActorSystem("EventSubscriberTests")
}

/**
 * TMT Source Code: 9/17/16.
 */
class EventSubscriberTests extends TestKit(EventSubscriberTests.sys) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {

  import system._
  implicit val timeout = Timeout(20.seconds)

  // Get the event service by looking up the name with the location service.
  private val eventService = Await.result(EventService(), timeout.duration)
  logger.info("Got Event Service!")

  override protected def beforeEach(): Unit = {
    TestEnv.resetRedisServices()
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  import assemblyContext._

  def newTestEventSubscriber(nssInUseIn: BooleanItem, followActor: Option[ActorRef], eventService: EventService): TestActorRef[TromboneEventSubscriber] = {
    val props = TromboneEventSubscriber.props(assemblyContext, nssInUseIn, followActor, eventService)
    TestActorRef(props)
  }

  def newEventSubscriber(nssInUse: BooleanItem, followActor: Option[ActorRef], eventService: EventService): ActorRef = {
    val props = TromboneEventSubscriber.props(assemblyContext, nssInUse, followActor, eventService)
    system.actorOf(props)
  }

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(a: ActorRef*): Unit = {
    val monitor = TestProbe()
    a.foreach { actorRef =>
      monitor.watch(actorRef)
      system.stop(actorRef)
      monitor.expectTerminated(actorRef)
    }
  }

  describe("basic event subscriber tests") {

    it("should be created with no issues") {
      // test1
      val fakeFollowActor = TestProbe()

      val es = newTestEventSubscriber(setNssInUse(false), Some(fakeFollowActor.ref), eventService)

      es.underlyingActor.nssZenithAngle should equal(za(0.0))
      es.underlyingActor.initialFocusError should equal(fe(0.0))
      es.underlyingActor.initialZenithAngle should equal(za(0.0))
      es.underlyingActor.nssInUseGlobal shouldBe setNssInUse(false)

      es ! StopFollowing
      fakeFollowActor.expectNoMsg(500.milli)
      cleanup(es)
    }
  }

  describe("tests for proper operation") {
    import AssemblyTestData._

    it("should make one event for an fe publish nssInUse") {
      // test2
      val fakeFollowActor = TestProbe()

      val es = newEventSubscriber(setNssInUse(true), Some(fakeFollowActor.ref), eventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = eventService

      // Default ZA is 0.0
      val testFE = 10.0
      // Publish a single focus error. This will generate a published event
      Await.ready(tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE))), 2.seconds)

      val msg = fakeFollowActor.expectMsgClass(classOf[UpdatedEventData])

      msg.focusError should equal(fe(testFE))
      // 0.0 is the default value as well as nssZenithAngle
      msg.zenithAngle should equal(za(0.0))

      // No more messages please
      fakeFollowActor.expectNoMsg(500.milli)
      cleanup(es)
    }

    it("should make several events for an fe list publish with nssInUse but no ZA") {
      // test3
      val fakeFollowActor = TestProbe()

      val es = newEventSubscriber(setNssInUse(true), Some(fakeFollowActor.ref), eventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = eventService

      // Publish a single focus error. This will generate a published event
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      feEvents.foreach(f => tcsRtc.publish(f))

      val msgs2 = fakeFollowActor.receiveN(feEvents.size, timeout.duration)
      msgs2.size shouldBe feEvents.size

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Should get no tcsEvents because not following
      tcsEvents.foreach(f => tcsRtc.publish(f))

      fakeFollowActor.expectNoMsg(500.milli)
      cleanup(es)
    }

    it("now enable follow should make several events for za and fe list publish nssNotInUse") {
      // test4
      val fakeFollowActor = TestProbe()

      val es = newEventSubscriber(setNssInUse(false), Some(fakeFollowActor.ref), eventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = eventService

      // Publish a single focus error. This will generate a published event
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      feEvents.foreach(f => tcsRtc.publish(f))

      val feEventMsgs: Vector[UpdatedEventData] = fakeFollowActor.receiveN(feEvents.size, timeout.duration).asInstanceOf[Vector[UpdatedEventData]]
      feEventMsgs.size should equal(feEvents.size)
      val fevals = feEventMsgs.map(f => f.focusError.head)
      // Should equal test vals
      fevals should equal(testFocusErrors)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Should get no tcsEvents because not following
      tcsEvents.foreach(f => tcsRtc.publish(f))

      // Should get several and the zenith angles should match since nssInUse was false
      val msgs: Vector[UpdatedEventData] = fakeFollowActor.receiveN(tcsEvents.size, timeout.duration).asInstanceOf[Vector[UpdatedEventData]]
      val zavals = msgs.map(f => f.zenithAngle.head)
      // Should equal input za
      zavals should equal(testZenithAngles)

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Now turn it off
      es ! StopFollowing
      // Give a little wait for the usubscribe to kick in before the publish events
      fakeFollowActor.expectNoMsg(200.milli)

      // Should get no tcsEvents because not following
      tcsEvents.foreach(f => tcsRtc.publish(f))

      // No more messages please
      fakeFollowActor.expectNoMsg(500.milli)

      cleanup(es)
    }

    it("alter nssInUse to see switch to nssZenithAngles") {
      // test5
      val fakeFollowActor = TestProbe()

      // Create with nssNotInuse so we get za events
      val es = newEventSubscriber(setNssInUse(false), Some(fakeFollowActor.ref), eventService)

      // first test that events are created for published focus error events
      // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
      val tcsRtc = eventService

      // Publish a single focus error. This will generate a published event
      val feEvents = testFocusErrors.map(f => SystemEvent(focusErrorPrefix).add(fe(f)))

      // These are fake messages for the FollowActor that will be sent to simulate the TCS
      val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

      val testZA = 45.0
      tcsRtc.publish(SystemEvent(zaConfigKey.prefix).add(za(testZA)))
      val one = fakeFollowActor.expectMsgClass(timeout.duration, classOf[UpdatedEventData])
      one.zenithAngle.head shouldBe testZA

      // Now follow with nssInUse and send feEvents, should have 0.0 as ZA
      es ! UpdateNssInUse(setNssInUse(true))

      // Now send the events
      feEvents.foreach(tcsRtc.publish)

      val msgs2: Vector[UpdatedEventData] = fakeFollowActor.receiveN(feEvents.size).asInstanceOf[Vector[UpdatedEventData]]

      // Each zenith angle with the message should be 0.0 now, not 45.0
      val zavals = msgs2.map(f => f.zenithAngle.head)
      zavals.filter(_ != 0.0) shouldBe empty

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Should get no tcsEvents because nssInUse = true
      tcsEvents.foreach(f => tcsRtc.publish(f))

      // No more messages please
      fakeFollowActor.expectNoMsg(100.milli)

      // Now turn it off
      es ! StopFollowing

      // Give a little wait for the usubscribe to kick in before the publish events
      fakeFollowActor.expectNoMsg(200.milli)

      // Should get no tcsEvents because not following
      tcsEvents.foreach(f => tcsRtc.publish(f))

      // No more messages please
      fakeFollowActor.expectNoMsg(200.milli)

      cleanup(es)
    }
  }

}
