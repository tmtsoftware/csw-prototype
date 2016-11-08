package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.ccs.AssemblyController2.Submit
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.services.events.EventService
import csw.services.loc.LocationService
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.Supervisor3._
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

  override def afterAll = {
    TestKit.shutdownActorSystem(TromboneAssemblyBasicTests.system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext

  import assemblyContext._

  implicit val timeout = Timeout(10.seconds)
  // Get the event service by looking up the name with the location service.
  val eventService = Await.result(EventService(), timeout.duration)

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

  def newTestTrombone(supervisor: ActorRef, assemblyInfo: AssemblyInfo = assemblyContext.info): TestActorRef[TromboneAssembly] = {
    val props = getTromboneProps(assemblyInfo, Some(supervisor))
    TestActorRef(props)
  }

  describe("low-level instrumented trombone assembly tests") {

    it("should get initialized with configs from files (same as AlgorithmData") {
      val supervisor = TestProbe()
      val tla = newTestTrombone(supervisor.ref)

      //      Thread.sleep(3000) // XXX allow for timeout if config service not running

      tla.underlyingActor.controlConfig.stageZero should be(AssemblyTestData.TestControlConfig.stageZero)
      tla.underlyingActor.controlConfig.positionScale should be(AssemblyTestData.TestControlConfig.positionScale)
      tla.underlyingActor.controlConfig.minStageEncoder should be(AssemblyTestData.TestControlConfig.minStageEncoder)

      tla.underlyingActor.calculationConfig.defaultInitialElevation should be(AssemblyTestData.TestCalculationConfig.defaultInitialElevation)
      tla.underlyingActor.calculationConfig.focusErrorGain should be(AssemblyTestData.TestCalculationConfig.focusErrorGain)
      tla.underlyingActor.calculationConfig.lowerFocusLimit should be(AssemblyTestData.TestCalculationConfig.lowerFocusLimit)
      tla.underlyingActor.calculationConfig.upperFocusLimit should be(AssemblyTestData.TestCalculationConfig.upperFocusLimit)
      tla.underlyingActor.calculationConfig.zenithFactor should be(AssemblyTestData.TestCalculationConfig.zenithFactor)

      expectNoMsg(2.seconds)
    }

    it("should lifecycle properly with a fake supervisor") {
      val fakeSupervisor = TestProbe()
      val tla = newTestTrombone(fakeSupervisor.ref)

      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)

      fakeSupervisor.send(tla, Running)

      fakeSupervisor.send(tla, DoShutdown)
      fakeSupervisor.expectMsg(ShutdownComplete)
      logger.info("Shutdown Complete")
    }

    it("datum without an init should fail") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow a datum") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should show a move without a datum as an error because trombone in wrong state") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, datum then 2 moves") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, datum then a position") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, datum then a set of positions as separate sca") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, datum then move and stop") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, setElevation") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should get an error for SetAngle without fillowing after good setup") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, setElevation, follow, stop") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }

    it("should allow an init, setElevation, follow, 2 setAngles, and a stop") {
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

      val monitor = TestProbe()
      monitor.watch(tromboneAssembly)
      tromboneAssembly ! PoisonPill
      monitor.expectTerminated(tromboneAssembly)
    }
  }

  it("should allow an init, setElevation, follow, a bunch of events and a stop") {

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
    tcsEvents.map { f =>
      logger.info(s"Publish: $f")
      tcsRtc.publish(f)
    }

    expectNoMsg(10.seconds)

    val monitor = TestProbe()
    monitor.watch(tromboneAssembly)
    tromboneAssembly ! PoisonPill
    monitor.expectTerminated(tromboneAssembly)

  }

}
