package csw.examples.vslice.assembly

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.AssemblyController2.Submit
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService
import csw.services.pkg.Component.{AssemblyInfo, HcdInfo, RegisterAndTrackServices}
import csw.services.pkg.Supervisor3
import csw.services.pkg.Supervisor3._
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.Configurations
import csw.util.config.Configurations.SetupConfig
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.concurrent.duration._

object TromboneAssemblyBasicTests {
  LocationService.initInterface()

  val system = ActorSystem("TromboneAssemblyBasicTests")
}

/**
 * TMT Source Code: 8/23/16.
 */
class TromboneAssemblyBasicTests extends TestKit(TromboneAssemblyBasicTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  // Initialize HCD for testing
  def startHCD: ActorRef = {
    val testInfo = HcdInfo(
      TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      RegisterAndTrackServices, Set(AkkaType), 1.second
    )

    Supervisor3(testInfo)
  }

  override def afterAll = {
    TestKit.shutdownActorSystem(TromboneAssemblyBasicTests.system)
  }

  val assemblyContext = AssemblyTestData.TestAssemblyContext
  import assemblyContext._

  def getTromboneProps(assemblyInfo: AssemblyInfo, supervisorIn: Option[ActorRef]): Props = {
    supervisorIn match {
      case None           => TromboneAssembly.props(assemblyInfo, TestProbe().ref)
      case Some(actorRef) => TromboneAssembly.props(assemblyInfo, actorRef)
    }
  }

  def newTrombone(assemblyInfo: AssemblyInfo = assemblyContext.info): (TestProbe, ActorRef) = {
    val supervisor = TestProbe()
    val props = getTromboneProps(assemblyInfo, Some(supervisor.ref))
    (supervisor, system.actorOf(props))
  }

  def newTestTrombone(assemblyInfo: AssemblyInfo = assemblyContext.info): (TestProbe, TestActorRef[TromboneAssembly]) = {
    val supervisor = TestProbe()
    val props = getTromboneProps(assemblyInfo, Some(supervisor.ref))
    (supervisor, TestActorRef(props))
  }

  describe("low-level instrumented trombone assembly tests") {

    it("should get initialized with configs from files (same as AlgorithmData") {
      val (_, tla) = newTestTrombone()

      tla.underlyingActor.controlConfig.stageZero should be(AssemblyTestData.TestControlConfig.stageZero)
      tla.underlyingActor.controlConfig.positionScale should be(AssemblyTestData.TestControlConfig.positionScale)
      tla.underlyingActor.controlConfig.minStageEncoder should be(AssemblyTestData.TestControlConfig.minStageEncoder)

      tla.underlyingActor.calculationConfig.defaultInitialElevation should be(AssemblyTestData.TestCalculationConfig.defaultInitialElevation)
      tla.underlyingActor.calculationConfig.focusErrorGain should be(AssemblyTestData.TestCalculationConfig.focusErrorGain)
      tla.underlyingActor.calculationConfig.lowerFocusLimit should be(AssemblyTestData.TestCalculationConfig.lowerFocusLimit)
      tla.underlyingActor.calculationConfig.upperFocusLimit should be(AssemblyTestData.TestCalculationConfig.upperFocusLimit)
      tla.underlyingActor.calculationConfig.zenithFactor should be(AssemblyTestData.TestCalculationConfig.zenithFactor)

      expectNoMsg(5.seconds)
    }

    it("should lifecycle properly with a fake supervisor") {
      val (supervisor, tla) = newTestTrombone()

      supervisor.expectMsg(Initialized)
      supervisor.expectMsg(Started)

      supervisor.send(tla, Running)

      supervisor.send(tla, DoShutdown)
      supervisor.expectMsg(ShutdownComplete)

    }

    it("should read conf file") {

      val f = new File("csw.examples.vslice.tromboneAssembly.conf")
      info(s"ex: ${f.exists()}")

      //val config = ConfigFactory.parseFileAnySyntax(new File("tromboneAssembly.conf"))
      val config = ConfigFactory.parseResources("tromboneAssembly.conf")

      info("Its: " + config)

      val name = config.getString("csw.examples.trombone.assembly.name")
      info("Name: " + name)
    }

    it("should allow a datum") {
      val (fakeSupervisor, tla) = newTrombone()
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tla, Running)

      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK))

      fakeClient.send(tla, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted

      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.status(0) shouldBe Completed
      // Wait a bit to see if there is any spurious messages
      fakeClient.expectNoMsg(250.milli)
    }

    it("should show a move without a datum as an error because trombone in wrong state") {
      val (fakeSupervisor, tla) = newTrombone()
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tla, Running)

      val testPosition = 90.0
      val sca = Configurations.createSetupConfigArg("testobsId", moveSC(testPosition))

      fakeClient.send(tla, Submit(sca))

      // This first one is the accept/verification -- note that it is accepted because there is no static validation errors
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted
      // This should fail due to wrong internal state
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe Incomplete
      completeMsg.details.status(0) shouldBe a[NoLongerValid]
      completeMsg.details.status(0).asInstanceOf[NoLongerValid].issue shouldBe a[WrongInternalStateIssue]
    }

    it("should allow a datum then 2 moves") {
      val (fakeSupervisor, tla) = newTrombone()
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tla, Running)

      val testMove = 90.0
      val testMove2 = 100.0
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK), moveSC(testMove), moveSC(testMove2))

      fakeClient.send(tla, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size
    }

    it("should allow a datum then a position") {
      val (fakeSupervisor, tla) = newTrombone()
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tla, Running)

      val testRangeDistance = 125.0
      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK), positionSC(testRangeDistance))

      fakeClient.send(tla, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      val completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size
    }

    it("should allow a datum then a set of positions as separate sca") {
      val (fakeSupervisor, tla) = newTrombone()
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tla, Running)

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK))
      fakeClient.send(tla, Submit(datum))

      // This first one is the accept/verification
      var acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      var completeMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted

      // This will send a config arg with 10 position commands
      val testRangeDistance = 90 to 180 by 10
      val positionConfigs = testRangeDistance.map(f => positionSC(f))

      val sca = Configurations.createSetupConfigArg("testobsId", positionConfigs: _*)
      fakeClient.send(tla, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones - give this some extra time to complete
      completeMsg = fakeClient.expectMsgClass(10.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size
    }

    it("should allow a datum then move and stop") {
      val (fakeSupervisor, tla) = newTrombone()
      val fakeClient = TestProbe()

      //val fakeSupervisor = TestProbe()
      fakeSupervisor.expectMsg(Initialized)
      fakeSupervisor.expectMsg(Started)
      fakeSupervisor.expectNoMsg(200.milli)
      fakeSupervisor.send(tla, Running)

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK))
      fakeClient.send(tla, Submit(datum))

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
      fakeClient.send(tla, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeClient.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted

      // Now send the stop after a bit of delay to let it get going
      // This is a timing thing that may not work on all machines
      fakeSupervisor.expectNoMsg(200.millis)
      val stop = Configurations.createSetupConfigArg("testobsId", SetupConfig(stopCK))
      // Send the stop
      fakeClient.send(tla, Submit(stop))

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
    }
  }
}
