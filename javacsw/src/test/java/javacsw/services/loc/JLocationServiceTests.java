package javacsw.services.loc;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection.*;
import csw.services.loc.LocationService;
import csw.services.loc.LocationService.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static javacsw.services.loc.JComponentType.Assembly;

/**
 * Tests the Java API to the location service
 * (Ported from the Scala LocationServiceTests class)
 */
public class JLocationServiceTests {
    private static ActorSystem system;

    // Amount of time to wait for a reply in tests before timing out
    private static FiniteDuration t = FiniteDuration.create(20, "seconds");

    public JLocationServiceTests() {
        LocationService.initInterface();
    }

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }


    @Test
    public void TestLocationServiceRegisterWithOnly() throws Exception {
        ComponentId componentId = JComponentId.componentId("TestAss1b", Assembly);
        TestProbe trackerResponseProbe = new TestProbe(system);
        TestProbe actorTestProbe = new TestProbe(system);
        AkkaConnection akkaConnection = JConnection.akkaConnection(componentId);
        AkkaRegistration akkaRegister = JLocationService.getAkkaRegistration(akkaConnection, actorTestProbe.ref(), "test.prefix1");
        ActorRef tracker = system.actorOf(JRegistrationTracker.props(Collections.singleton(akkaRegister), trackerResponseProbe.ref()));
        ComponentRegistered m1 = trackerResponseProbe.expectMsgClass(t, ComponentRegistered.class);
        assert(m1.connection().equals(akkaConnection));
        m1.result().unregister();
        system.stop(tracker);
    }

    @Test
    public void TestLocationServiceRegisterWithBothAkkaAndHttpAsSequence() throws Exception {
        ComponentId componentId = JComponentId.componentId("TestAss2b", Assembly);
        TestProbe trackerResponseProbe = new TestProbe(system);
        TestProbe actorTestProbe = new TestProbe(system);
        AkkaConnection akkaConnection = JConnection.akkaConnection(componentId);
        HttpConnection httpConnection = JConnection.httpConnection(componentId);
        AkkaRegistration akkaRegister = JLocationService.getAkkaRegistration(akkaConnection, actorTestProbe.ref(), "test.prefix2");
        HttpRegistration httpRegister = JLocationService.getHttpRegistration(httpConnection, 1000, "test.prefix2");
        Set<Registration> registrations = new HashSet<>(Arrays.asList(akkaRegister, httpRegister));
        ActorRef tracker = system.actorOf(JRegistrationTracker.props(registrations, trackerResponseProbe.ref()));
        List<ComponentRegistered> m1 = Arrays.asList(
                trackerResponseProbe.expectMsgClass(t, ComponentRegistered.class),
                trackerResponseProbe.expectMsgClass(t, ComponentRegistered.class));
        assert(m1.size() == 2);
        assert(m1.stream().anyMatch(r -> r.connection().equals(akkaConnection)));
        assert(m1.stream().anyMatch(r -> r.connection().equals(httpConnection)));
        m1.stream().forEach(r -> r.result().unregister());
        system.stop(tracker);
    }


    @Test
    public void TestTrackerWithOneAkkaComponent() throws Exception {
        ComponentId componentId = JComponentId.componentId("TestAss3b", Assembly);
        TestProbe testProbe = new TestProbe(system);
        TestProbe actorTestProbe = new TestProbe(system);
        CompletableFuture<RegistrationResult> f = JLocationService.registerAkkaConnection(componentId, actorTestProbe.ref(), "test.prefix3", system);

    }
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)), "LocationTracker!")
//
//        val ac = AkkaConnection(componentId)
//
//        tracker ! TrackConnection(ac)
//
//        testProbe.expectMsg(t, Unresolved(ac))
//
//        val ready = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
//        assert(ready.connection == ac)
//        //    expectNoMsg(5.seconds)
//
//        val result = Await.result(f, 1.second)
//        result.unregister()
//        system.stop(tracker)
//    }
//
//        test("Test tracker with one Akka component - try to add twice") {
//        import LocationService._
//
//        val componentId = ComponentId("TestAss4b", Assembly)
//        val testPrefix = "test.prefix4"
//
//        val testProbe = TestProbe()
//        val actorTestProbe = TestProbe()
//
//        val f = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)
//
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))
//
//        val ac = AkkaConnection(componentId)
//
//        tracker ! TrackConnection(ac)
//
//        testProbe.expectMsg(t, Unresolved(ac))
//
//        val ready = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
//        assert(ready.connection == ac)
//
//        //    expectNoMsg(5.seconds)
//
//        tracker ! TrackConnection(ac)
//        //    expectNoMsg(5.seconds)
//        val result = Await.result(f, 1.second)
//        result.unregister()
//        system.stop(tracker)
//    }
//
//        test("Test tracker with one HTTP component") {
//        import LocationService._
//
//        val componentId = ComponentId("TestAss5b", Assembly)
//        val testPrefix = "test.prefix5"
//        val testPort = 1000
//
//        val testProbe = TestProbe()
//
//        val f = LocationService.registerHttpConnection(componentId, testPort)
//
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))
//
//        val hc = HttpConnection(componentId)
//
//        tracker ! TrackConnection(hc)
//
//        testProbe.expectMsg(t, Unresolved(hc))
//
//        val ready = testProbe.expectMsgClass(10.seconds, classOf[ResolvedHttpLocation])
//        assert(ready.connection == hc)
//
//        //    expectNoMsg(5.seconds)
//        val result = Await.result(f, 1.second)
//        result.unregister()
//        system.stop(tracker)
//    }
//
//        test("Test tracker with two components registered before tracker") {
//        import LocationService._
//
//        val componentId = ComponentId("TestAss6b", Assembly)
//        val testPrefix = "test.prefix6"
//        val testPort = 1000
//
//        val testProbe = TestProbe()
//
//        val actorTestProbe = TestProbe()
//
//        val fList = List(
//                LocationService.registerHttpConnection(componentId, testPort),
//                LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)
//        )
//
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))
//
//        val ac = AkkaConnection(componentId)
//        val hc = HttpConnection(componentId)
//
//        tracker ! TrackConnection(ac)
//        testProbe.expectMsg(20.seconds, Unresolved(ac))
//        val r1 = testProbe.expectMsgClass(20.seconds, classOf[ResolvedAkkaLocation])
//        assert(r1.connection == ac)
//
//        //    expectNoMsg(5.seconds) // Give time for all to be registered
//
//        tracker ! TrackConnection(hc)
//        testProbe.expectMsg(20.seconds, Unresolved(hc))
//        val r2 = testProbe.expectMsgClass(20.seconds, classOf[ResolvedHttpLocation])
//        assert(r2.connection == hc)
//
//        // Assure no messages coming for no tracking
//        //    testProbe.expectNoMsg(5.seconds)
//
//        val f = Future.sequence(fList)
//        val resultList = Await.result(f, 1.second)
//        resultList.foreach(_.unregister())
//        system.stop(tracker)
//    }
//
//        test("Test tracker to ensure no messages without a registered comp") {
//        import LocationService._
//
//        val testProbe = TestProbe()
//
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))
//
//        // Assure no messages coming for no tracking
//        testProbe.expectNoMsg(5.seconds)
//        system.stop(tracker)
//    }
//
//        test("Test tracker with two components register later") {
//        import LocationService._
//
//        val componentId = ComponentId("TestAss7b", Assembly)
//        val testPrefix = "test.prefix7"
//        val testPort = 1000
//
//        val testProbe = TestProbe()
//
//        val actorTestProbe = TestProbe()
//
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))
//
//        val ac = AkkaConnection(componentId)
//        val hc = HttpConnection(componentId)
//
//        tracker ! TrackConnection(ac)
//        tracker ! TrackConnection(hc)
//        testProbe.expectMsg(t, Unresolved(ac))
//        testProbe.expectMsg(t, Unresolved(hc))
//
//        // Assure no messages coming for no tracking
//        //    testProbe.expectNoMsg(5.seconds)
//
//        val f1 = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)
//
//        val r1 = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
//        assert(r1.connection == ac)
//
//        val f2 = LocationService.registerHttpConnection(componentId, testPort)
//        val r2 = testProbe.expectMsgClass(t, classOf[ResolvedHttpLocation])
//        assert(r2.connection == hc)
//        // Assure no messages coming for no tracking
//        //    testProbe.expectNoMsg(5.seconds)
//
//        val f = Future.sequence(List(f1, f2))
//        val resultList = Await.result(f, 1.second)
//        resultList.foreach(_.unregister())
//        system.stop(tracker)
//    }
//
//        test("Test tracker with two components then remove one") {
//        import LocationService._
//
//        val componentId = ComponentId("TestAss8b", Assembly)
//        val testPrefix = "test.prefix8"
//        val testPort = 1000
//
//        val testProbe = TestProbe()
//
//        val actorTestProbe = TestProbe()
//
//        val tracker = system.actorOf(LocationTracker.props(Some(testProbe.ref)))
//
//        val ac = AkkaConnection(componentId)
//        val hc = HttpConnection(componentId)
//
//        tracker ! TrackConnection(ac)
//        tracker ! TrackConnection(hc)
//        testProbe.expectMsg(t, Unresolved(ac))
//        testProbe.expectMsg(t, Unresolved(hc))
//
//        // Assure no messages coming for no tracking
//        //    testProbe.expectNoMsg(5.seconds)
//
//        val f1 = LocationService.registerAkkaConnection(componentId, actorTestProbe.ref, testPrefix)
//
//        val r1 = testProbe.expectMsgClass(10.seconds, classOf[ResolvedAkkaLocation])
//        assert(r1.connection == ac)
//
//        val f2 = LocationService.registerHttpConnection(componentId, testPort)
//        val r2 = testProbe.expectMsgClass(t, classOf[ResolvedHttpLocation])
//        assert(r2.connection == hc)
//
//        tracker ! UntrackConnection(hc)
//
//        val r3 = testProbe.expectMsgClass(t, classOf[UnTrackedLocation])
//        assert(r3.connection == hc)
//
//        // Re-add it again
//        tracker ! TrackConnection(hc)
//        testProbe.expectMsg(t, Unresolved(hc))
//        val r4 = testProbe.expectMsgClass(t, classOf[ResolvedHttpLocation])
//        assert(r4.connection == hc)
//        // Assure no messages coming for no tracking
//        //    testProbe.expectNoMsg(5.seconds)
//
//        val f = Future.sequence(List(f1, f2))
//        val resultList = Await.result(f, 1.second)
//        resultList.foreach(_.unregister())
//        system.stop(tracker)
//    }
//    }



}
