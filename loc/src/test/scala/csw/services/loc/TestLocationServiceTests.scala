package csw.services.loc

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationServiceProvider.{Location, ResolvedAkkaLocation, TrackConnection, Unresolved}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, ShouldMatchers}

/**
 * TMT Source Code: 8/4/16.
 */
class TestLocationServiceTests extends TestKit(ActorSystem("TromboneTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  val comp1 = ComponentId("test1", ComponentType.HCD)
  val comp2 = ComponentId("test2", ComponentType.Assembly)
  val comp3 = ComponentId("test3", ComponentType.Assembly)

  describe("Registeration/unregistering tests") {
    import TestLocationService._
    import system.dispatcher

    it("should allow registering a test probe") {
      initInterface()
      val comp1tp = TestProbe()
      val comp2tp = TestProbe()

      val r1 = TestLocationService.registerAkkaConnection(comp1, comp1tp.ref, "WFOS.blue.filter")
      r1.map(r => r.componentId should equal(comp1))

      val c1 = AkkaConnection(comp1)

      connections.size should be(1)
      connections.isDefinedAt(c1) should equal(true)
      // Assume present
      val loc1: ResolvedAkkaLocation = connections(c1).asInstanceOf[ResolvedAkkaLocation]
      loc1.actorRef should be(Option(comp1tp.ref))

      val r2 = registerAkkaConnection(comp2, comp2tp.ref, "WFOS.blue.filterHCD")
      r2.map(_.componentId should be(comp2))

      val c2 = AkkaConnection(comp2)

      connections.size should be(2)
      connections.isDefinedAt(c2) should equal(true)

      val loc2: ResolvedAkkaLocation = connections(c1).asInstanceOf[ResolvedAkkaLocation]
      loc2.actorRef should be(Option(comp1tp.ref))

      // Try an HCD connection
      val r3 = registerHttpConnection(comp2, 8000, "/a/b/c")
      r3.map(_.componentId should be(comp2))
      connections.size should be(3)
      //Stopping, HTTP connection not that useful right now
    }

    it("should allow unregistering") {
      initInterface()
      val comp1tp = TestProbe()
      val comp2tp = TestProbe()
      val comp3tp = TestProbe()

      val r1 = registerAkkaConnection(comp1, comp1tp.ref, "WFOS.test1")
      r1.map(_.componentId should equal(comp1))
      connections.size should be(1)
      connections.isDefinedAt(AkkaConnection(comp1)) should equal(true)

      val r2 = registerAkkaConnection(comp2, comp2tp.ref, "WFOS.test2")
      r2.map(_.componentId should be(comp2))
      connections.size should be(2)
      connections.isDefinedAt(AkkaConnection(comp2)) should equal(true)

      val r3 = registerAkkaConnection(comp3, comp3tp.ref, "WFOS.test3")
      r3.map(_.componentId should be(comp3))
      connections.size should be(3)
      connections.isDefinedAt(AkkaConnection(comp3)) should equal(true)

      val c1 = AkkaConnection(comp1)
      unregisterConnection(c1)
      connections.size should be(3) // Stays at 3
      connections.get(c1).get should be(Unresolved(c1))

      val c2 = AkkaConnection(comp2)
      unregisterConnection(c2)
      connections.size should be(3) // Stays at 3
      connections.get(c2).get should be(Unresolved(c2))
    }
  }

  describe("Should allow a tracker") {
    import TestLocationService._
    import system.dispatcher

    it("Should allow tracking that follows registrations through service") {
      initInterface()

      val tclient1 = TestProbe()

      val comp1tp = TestProbe()
      val comp2tp = TestProbe()
      val comp3tp = TestProbe()

      val r1 = registerAkkaConnection(comp1, comp1tp.ref, "WFOS.test1")
      r1.map(_.componentId should equal(comp1))
      connections.size should be(1)

      val p1: Props = trackerProps(Some(tclient1.ref))

      val t1 = TestActorRef[TestLocationTracker](p1)

      val r2 = registerAkkaConnection(comp2, comp2tp.ref, "WFOS.test2")
      r2.map(_.componentId should be(comp2))
      connections.size should be(2)
    }

    it("Should allow tracking to tracker") {
      initInterface()

      val tclient1 = TestProbe()

      val comp1tp = TestProbe()
      val comp2tp = TestProbe()
      val comp3tp = TestProbe()

      val r1 = registerAkkaConnection(comp1, comp1tp.ref, "WFOS.test1")
      r1.map(_.componentId should equal(comp1))
      connections.size should be(1)

      val p1: Props = trackerProps(Some(tclient1.ref))
      val t1 = TestActorRef[TestLocationTracker](p1)

      // Tracking a registered connectino should result in a received message
      tclient1.send(t1, TrackConnection(AkkaConnection(comp1)))
      val loc = tclient1.expectMsgClass(classOf[ResolvedAkkaLocation])
      info("Loc: " + loc)
      loc.connection should be(AkkaConnection(comp1))
      loc.prefix should equal("WFOS.test1")
      loc.actorRef should equal(Some(comp1tp.ref))
    }
  }

  class MyFakeAssembly extends Actor with LocationTrackerClientActor3 {
    val lservice: LocationServiceProvider = TestLocationService

    // This is defining the correct tracker
    def tracker = context.actorOf(lservice.trackerProps(Some(context.self)))

    def receive = trackerClientReceive
  }

  describe("Should work with an actor trait") {
    import TestLocationService._
    import system.dispatcher
    import scala.concurrent.duration._

    it("Should get updates in an actor") {
      initInterface()

      // Fake component
      val fakeComp1 = TestProbe()
      val fakeConnection = AkkaConnection(comp1)
      val r1 = registerAkkaConnection(comp1, fakeComp1.ref, "WFOS.test1")
      val m1 = r1.map(_.componentId should equal(comp1))

      // Register another
      val fakeComp2 = TestProbe()
      val fakeConnection2 = AkkaConnection(comp2)

      val r2 = registerAkkaConnection(comp2, fakeComp2.ref, "WFOS.test2")
      val m2 = r2.map(_.componentId should equal(comp2))

      // Now create a fake assembly
      val x: TestActorRef[MyFakeAssembly] = TestActorRef(new MyFakeAssembly)

      // Assembly asks to track one of its connections
      x.underlyingActor.trackConnection(fakeConnection)
      // Short wait here for message from Tracker to be received
      expectNoMsg(10.milliseconds)
      x.underlyingActor.allResolved should be(true)
      var loc = x.underlyingActor.getLocation(fakeConnection).map(_.asInstanceOf[ResolvedAkkaLocation]).get
      loc.actorRef should be(Option(fakeComp1.ref))

      x.underlyingActor.getLocations.size should be(1)

      // Assembly asks to track one of its connections
      x.underlyingActor.trackConnection(fakeConnection2)
      // Short wait here for message from Tracker to be received
      expectNoMsg(10.milliseconds)
      x.underlyingActor.allResolved should be(true)
      loc = x.underlyingActor.getLocation(fakeConnection2).map(_.asInstanceOf[ResolvedAkkaLocation]).get
      loc.actorRef should be(Option(fakeComp2.ref))
      expectNoMsg(10.milli)
      x.underlyingActor.getLocations.size should be(2)

      // Should allow an untrack
      x.underlyingActor.untrackConnection(fakeConnection2)
      expectNoMsg(10.milli)
      x.underlyingActor.getLocations.size should be(1)
      x.underlyingActor.getLocation(fakeConnection2) should equal(None)
      loc = x.underlyingActor.getLocation(fakeConnection).map(_.asInstanceOf[ResolvedAkkaLocation]).get
      loc.actorRef should be(Option(fakeComp1.ref))
    }
  }

}
