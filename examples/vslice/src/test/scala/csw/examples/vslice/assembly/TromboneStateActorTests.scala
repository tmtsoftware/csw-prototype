package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import csw.examples.vslice.TestEnv
import csw.examples.vslice.assembly.TromboneStateActor.TromboneState
import csw.services.loc.LocationService
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Inspectors, _}

import scala.concurrent.duration._

/**
 * TMT Source Code: 10/22/16.
 */

object TromboneStateActorTests {
  LocationService.initInterface()
  val system = ActorSystem("TromboneStateActorTests")

  // Test subscriber actor for telemetry and system events
  object TestSubscriber {
    def props(): Props = Props(new TestSubscriber())

    case object GetResults

    case class Results(msgs: Vector[TromboneState])

    def newTestSubscriber(system: ActorSystem): ActorRef = system.actorOf(props())
  }

  /**
   * Test event service client, listens for trombonestate
   */
  class TestSubscriber() extends Actor with ActorLogging with TromboneStateClient {

    import TestSubscriber._

    var msgs = Vector.empty[TromboneState]

    log.info(s"Test subscriber for TromboneState")

    def receive: Receive = {
      case event: TromboneState =>
        msgs = msgs :+ event
        log.debug(s"Received system event: $event")

      case GetResults => sender() ! Results(msgs)
    }
  }
}

class TromboneStateActorTests extends TestKit(TromboneStateActorTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with Inspectors with BeforeAndAfterEach {

  import TromboneStateActorTests._
  import TromboneStateActor._

  override protected def beforeEach(): Unit = {
    TestEnv.resetRedisServices()
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

  describe("Testing state item") {

    it("should allow creation") {
      val ts = defaultTromboneState

      ts.cmd should equal(cmdDefault)
      ts.move should equal(moveDefault)
      ts.sodiumLayer should equal(sodiumLayerDefault)
      ts.nss should equal(nssDefault)
    }

    it("should allow changing just one") {
      var ts = defaultTromboneState

      ts = ts.copy(cmd = cmdItem(cmdReady))
      ts.cmd.head should equal(cmdReady)
      ts.move.head should equal(moveUnindexed)
      ts.sodiumLayer.head should equal(false)
      ts.nss.head should equal(false)
    }

    it("equals needs to work") {
      val ts1 = defaultTromboneState

      val ts2 = TromboneState(cmdItem(cmdReady), moveDefault, sodiumLayerDefault, nssDefault)

      ts1 should equal(ts1)
      ts1 shouldNot equal(ts2)

      val ts3 = ts2.copy(cmd = cmdDefault)
      ts1 should equal(ts3)

      val ts4 = ts3.copy(move = moveItem(moveIndexed))
      ts4 shouldNot equal(ts3)

      val ts5 = ts1.copy(sodiumLayer = sodiumItem(true))
      ts5 shouldNot equal(ts1)
      ts5 shouldNot equal(ts4)

      val ts6 = ts1.copy(nss = nssItem(true))
      ts6 shouldNot equal(ts1)
      ts6 shouldNot equal(ts5)
    }

  }

  it("it should allow publishing - check conditions") {
    import TestSubscriber._

    val tsa = system.actorOf(TromboneStateActor.props())

    val tsub = system.actorOf(TestSubscriber.props())

    val ts1 = defaultTromboneState

    val ts2 = TromboneState(cmdItem(cmdReady), moveDefault, sodiumLayerDefault, nssDefault)

    val ts3 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumLayerDefault, nssDefault)

    val ts4 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssDefault)

    val ts5 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(true))

    // Starts with default so ensure setting it doesn't cause publish
    tsa ! SetState(ts1)
    // Delays are to give time to subscriber to run
    // Should publish (1)
    tsa ! SetState(ts2)
    expectNoMsg(5.milli)
    // Try again should not be published
    tsa ! SetState(ts2)
    expectNoMsg(5.milli)
    // Should publish (2)
    tsa ! SetState(ts3)
    expectNoMsg(5.milli)
    // Should publish (3)
    tsa ! SetState(ts4)
    expectNoMsg(5.milli)
    // Should publish (4)
    tsa ! SetState(ts5)
    expectNoMsg(5.milli)

    tsub ! GetResults
    val result = expectMsgClass(classOf[TestSubscriber.Results])
    result.msgs.size shouldBe 4
    result shouldEqual Results(Vector(ts2, ts3, ts4, ts5))
    //info("Result: " + result.msgs)
    cleanup(tsa, tsub)
  }

  def setupState(ts: TromboneState) = {
    // These times are important to allow time for test actors to get and process the state updates when running tests
    expectNoMsg(20.milli)
    system.eventStream.publish(ts)
    // This is here to allow the destination to run and set its state
    expectNoMsg(20.milli)
  }

  it("it might work with publish directly") {
    import TestSubscriber._

    val tsub = system.actorOf(TestSubscriber.props())

    val ts1 = defaultTromboneState

    val ts2 = TromboneState(cmdItem(cmdReady), moveDefault, sodiumLayerDefault, nssDefault)

    val ts3 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumLayerDefault, nssDefault)

    val ts4 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssDefault)

    val ts5 = TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(true))

    setupState(ts2)
    setupState(ts3)

    tsub ! GetResults
    val result = expectMsgClass(classOf[TestSubscriber.Results])
    info("Result: " + result)
    cleanup(tsub)
  }

}
