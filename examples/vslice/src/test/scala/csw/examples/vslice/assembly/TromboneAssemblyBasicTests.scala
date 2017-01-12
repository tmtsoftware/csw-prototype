package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.services.apps.containerCmd.ContainerCmd
import csw.services.ccs.AssemblyController.Submit
import csw.services.ccs.CommandStatus._
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.services.events.EventService
import csw.services.loc.LocationService
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.Supervisor._
import csw.util.config.Configurations
import csw.util.config.Configurations.SetupConfig
import csw.util.config.Events.SystemEvent
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.Await
import scala.concurrent.duration._

object TromboneAssemblyBasicTests {
  LocationService.initInterface()

  val system = ActorSystem("TromboneAssemblyBasicTests")
}

/**
 * This test assumes an HCD is running.
 * It creates an Assembly for direct interaction, not using the Supervisor
 */
class TromboneAssemblyBasicTests extends TestKit(TromboneAssemblyBasicTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  import system._

  // List of top level actors that were created for the HCD (for clean up)
  var hcdActors: List[ActorRef] = Nil

  override def beforeAll: Unit = {
    TestEnv.createTromboneAssemblyConfig()

    // Starts the HCD used in the test
    val cmd = ContainerCmd("vslice", Array("--standalone"), Map("" -> "tromboneHCD.conf"))
    hcdActors = cmd.actors
  }

  override def afterAll: Unit = {
    hcdActors.foreach { actorRef =>
      watch(actorRef)
      actorRef ! HaltComponent
      expectTerminated(actorRef)
    }
    TestKit.shutdownActorSystem(TromboneAssemblyBasicTests.system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext

  import assemblyContext._

  implicit val timeout = Timeout(10.seconds)
  // Get the event service by looking up the name with the location service.
  private val eventService = Await.result(EventService(), timeout.duration)

  def getTromboneProps(assemblyInfo: AssemblyInfo, supervisorIn: Option[ActorRef]): Props = {
    supervisorIn match {
      case None           => TromboneAssembly.props(assemblyInfo, TestProbe().ref)
      case Some(actorRef) => TromboneAssembly.props(assemblyInfo, actorRef)
    }
  }

  def newTrombone(supervisor: ActorRef, assemblyInfo: AssemblyInfo = assemblyContext.info): ActorRef = {
    val props = getTromboneProps(assemblyInfo, Some(supervisor))
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

  describe("low-level instrumented trombone assembly tests") {

    it("should lifecycle properly with a fake supervisor") {
      // test2
      val fakeSupervisor = TestProbe()
      val tla = newTrombone(fakeSupervisor.ref)

      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(10.seconds, Started)

      fakeSupervisor.send(tla, Running)

      fakeSupervisor.send(tla, DoShutdown)
      fakeSupervisor.expectMsg(ShutdownComplete)
      logger.info("Shutdown Complete")

      cleanup(tla)
    }

    it("datum without an init should fail") {
      // test3
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.send(tromboneAssembly, Running)

      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK))

      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification succeeds because verification does not look at state
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted
      logger.info(s"Accepted: $acceptedMsg")

      // This should fail due to wrong internal state
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe Incomplete
      completeMsg.details.status(0) shouldBe a[NoLongerValid]
      completeMsg.details.status(0).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]

      cleanup(tromboneAssembly)
    }

    it("should allow a datum") {
      // test4
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.send(tromboneAssembly, Running)

      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted

      val completeMsg = fakeClient.expectMsgClass(5.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.status(0) shouldBe Completed
      // Wait a bit to see if there is any spurious messages
      fakeClient.expectNoMsg(250.milli)
      //logger.info("Completed: " + completeMsg)

      cleanup(tromboneAssembly)
    }

    it("should show a move without a datum as an error because trombone in wrong state") {
      // test5
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.send(tromboneAssembly, Running)

      // Sending an Init first so we can see the dataum issue
      val testPosition = 90.0
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), moveSC(testPosition))

      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted
      // This should fail due to wrong internal state
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("Completed Msg: " + completeMsg)
      completeMsg.overall shouldBe Incomplete
      // First completes no issue
      completeMsg.details.status(0) shouldBe Completed
      // Second is for move and it should be invalid
      completeMsg.details.status(1) shouldBe a[NoLongerValid]
      completeMsg.details.status(1).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]

      cleanup(tromboneAssembly)
    }

    it("should allow an init, datum then 2 moves") {
      // test6
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tromboneAssembly, Running)

      val testMove = 90.0
      val testMove2 = 100.0
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK), moveSC(testMove), moveSC(testMove2))

      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size

      cleanup(tromboneAssembly)
    }

    it("should allow an init, datum then a position") {
      // test7
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tromboneAssembly, Running)

      val testRangeDistance = 125.0
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK), positionSC(testRangeDistance))

      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      //logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size

      cleanup(tromboneAssembly)
    }

    it("should allow an init, datum then a set of positions as separate sca") {
      // test8
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tromboneAssembly, Running)

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
      fakeClient.send(tromboneAssembly, Submit(datum))

      // This first one is the accept/verification
      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      //logger.info("acceptedMsg: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("completeMsg: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted

      // This will send a config arg with 10 position commands
      val testRangeDistance = 90 to 180 by 10
      val positionConfigs = testRangeDistance.map(f => positionSC(f))

      val sca = Configurations.createSetupConfigArg("testobsId", positionConfigs: _*)
      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      //logger.info("acceptedMsg: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones - give this some extra time to complete
      completeMsg = fakeClient.expectMsgClass(10.seconds, classOf[CommandResult])
      logger.info("completeMsg: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size

      cleanup(tromboneAssembly)
    }

    it("should allow an init, datum then move and stop") {
      // test9
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tromboneAssembly, Running)

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
      fakeClient.send(tromboneAssembly, Submit(datum))

      // This first one is the accept/verification
      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted
      // Second one is completion of the executed datum
      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted

      // Now start a long move
      val testMove = 150.1
      val sca = Configurations.createSetupConfigArg("testobsId", moveSC(testMove))
      // Send the move
      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted

      // Now send the stop after a bit of delay to let it get going
      // This is a timing thing that may not work on all machines
      fakeSupervisor.expectNoMsg(200.millis)
      val stop = Configurations.createSetupConfigArg("testobsId", SetupConfig(stopCK))
      // Send the stop
      fakeClient.send(tromboneAssembly, Submit(stop))

      // Stop must be accepted too
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("acceptedmsg2: " + acceptedMsg)
      completeMsg.overall shouldBe AllCompleted

      // Second one is completion of the stop
      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg22: " + completeMsg)
      completeMsg.overall shouldBe Incomplete
      completeMsg.details.status(0) shouldBe Cancelled
      // Checking that no talking
      fakeClient.expectNoMsg(100.milli)

      cleanup(tromboneAssembly)
    }

    it("should allow an init, setElevation") {
      // test10
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tromboneAssembly, Running)

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
      fakeClient.send(tromboneAssembly, Submit(datum))

      // This first one is the accept/verification
      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted
      // Second one is completion of the executed datum
      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted

      val testEl = 150.0
      val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl))

      // Send the setElevation
      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted
      //logger.info(s"AcceptedMsg: $acceptedMsg")

      // Second one is completion of the executed ones
      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info(s"completeMsg: $completeMsg")
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size

      cleanup(tromboneAssembly)
    }

    it("should get an error for SetAngle without fillowing after good setup") {
      // test11
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.send(tromboneAssembly, Running)

      // Sending an Init first so we can see the datum issue
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("acceptedMsg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed init/datum
      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted
      logger.info(s"completedMsg1: $completeMsg")

      // Now try a setAngle
      val setAngleValue = 22.0
      val sca2 = Configurations.createSetupConfigArg("testobsId", setAngleSC(setAngleValue))
      // Send the command
      fakeClient.send(tromboneAssembly, Submit(sca2))

      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // This should fail due to wrong internal state
      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("Completed Msg2: " + completeMsg)

      completeMsg.overall shouldBe Incomplete
      // First is not valid
      completeMsg.details.status(0) shouldBe a[NoLongerValid]
      completeMsg.details.status(0).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]

      cleanup(tromboneAssembly)
    }

    it("should allow an init, setElevation, follow, stop") {
      // test12
      val fakeSupervisor = TestProbe()
      val tromboneAssembly = newTrombone(fakeSupervisor.ref)
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tromboneAssembly, Running)

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
      fakeClient.send(tromboneAssembly, Submit(datum))

      // This first one is the accept/verification
      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted
      // Second one is completion of the executed datum
      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted

      val testEl = 150.0
      val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl), followSC(false), SetupConfig(stopCK))

      // Send the setElevation
      fakeClient.send(tromboneAssembly, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted
      //logger.info(s"AcceptedMsg: $acceptedMsg")

      // Second one is completion of the executed ones
      completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info(s"completeMsg: $completeMsg")
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size

      cleanup(tromboneAssembly)
    }
  }

  it("should allow an init, setElevation, follow, a bunch of events and a stop") {
    // test13

    val fakeSupervisor = TestProbe()
    val tromboneAssembly = newTrombone(fakeSupervisor.ref)
    val fakeClient = TestProbe()

    //val fakeSupervisor = TestProbe()
    fakeSupervisor.expectMsg(Initialized)
    fakeSupervisor.expectMsg(Started)
    fakeSupervisor.expectNoMsg(200.milli)
    fakeSupervisor.send(tromboneAssembly, Running)

    val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
    fakeClient.send(tromboneAssembly, Submit(datum))

    // This first one is the accept/verification
    var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
    acceptedMsg.overall shouldBe Accepted
    // Second one is completion of the executed datum
    var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
    completeMsg.overall shouldBe AllCompleted

    val testEl = 150.0
    val sca = Configurations.createSetupConfigArg("testobsId", setElevationSC(testEl), followSC(false))

    // Send the setElevation
    fakeClient.send(tromboneAssembly, Submit(sca))

    // This first one is the accept/verification
    acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
    acceptedMsg.overall shouldBe Accepted
    //logger.info(s"AcceptedMsg: $acceptedMsg")

    // Second one is completion of the executed ones
    completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
    logger.info(s"completeMsg: $completeMsg")
    completeMsg.overall shouldBe AllCompleted

    // Now send some events
    // This eventService is used to simulate the TCS and RTC publishing zentith angle and focus error
    val tcsRtc = eventService

    val testFE = 10.0
    // Publish a single focus error. This will generate a published event
    tcsRtc.publish(SystemEvent(focusErrorPrefix).add(fe(testFE)))

    val testZenithAngles = 0.0 to 40.0 by 5.0
    // These are fake messages for the FollowActor that will be sent to simulate the TCS
    val tcsEvents = testZenithAngles.map(f => SystemEvent(zaConfigKey.prefix).add(za(f)))

    // This should result in the length of tcsEvents being published
    tcsEvents.foreach { f =>
      logger.info(s"Publish: $f")
      tcsRtc.publish(f)
    }

    expectNoMsg(10.seconds)

    cleanup(tromboneAssembly)
  }

}
