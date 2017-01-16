package csw.examples.vslice.assembly

/**
 * TMT Source Code: 10/10/16.
 */

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.TestEnv
import csw.services.apps.containerCmd.ContainerCmd
import csw.services.ccs.AssemblyController.Submit
import csw.services.ccs.CommandStatus.{Accepted, AllCompleted, CommandResult, Completed}
import csw.services.loc.LocationService
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.Supervisor
import csw.services.pkg.Supervisor._
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.Configurations
import csw.util.config.Configurations.SetupConfig
import org.scalatest.{BeforeAndAfterAll, _}

import scala.concurrent.duration._

object TromboneAssemblyCompTests {
  LocationService.initInterface()

  val system = ActorSystem("TromboneAssemblyCompTests")
}

class TromboneAssemblyCompTests extends TestKit(TromboneAssemblyCompTests.system) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  val assemblyContext = AssemblyTestData.TestAssemblyContext

  import assemblyContext._

  def newTrombone(assemblyInfo: AssemblyInfo = assemblyContext.info): ActorRef = {
    Supervisor(assemblyInfo)
  }

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(component: ActorRef): Unit = {
    val monitor = TestProbe()
    monitor.watch(component)
    component ! HaltComponent
    monitor.expectTerminated(component)
  }

  // List of top level actors that were created for the HCD (for clean up)
  var hcdActors: List[ActorRef] = Nil

  override def beforeAll: Unit = {
    TestEnv.createTromboneAssemblyConfig()

    // Starts the HCD used in the test
    val cmd = ContainerCmd("vslice", Array("--standalone"), Map("" -> "tromboneHCD.conf"))
    hcdActors = cmd.actors
  }

  override def afterAll: Unit = {
    hcdActors.foreach(cleanup)
    TestKit.shutdownActorSystem(TromboneAssemblyBasicTests.system)
  }

  describe("comp tests") {

    it("should just startup") {
      val tla = newTrombone()
      val fakeSequencer = TestProbe()

      tla ! SubscribeLifecycleCallback(fakeSequencer.ref)
      fakeSequencer.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeSequencer.expectMsg(LifecycleStateChanged(LifecycleRunning))
      cleanup(tla)
    }

    it("should allow a datum") {
      val tla = newTrombone()
      val fakeSequencer = TestProbe()

      tla ! SubscribeLifecycleCallback(fakeSequencer.ref)
      fakeSequencer.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeSequencer.expectMsg(LifecycleStateChanged(LifecycleRunning))

      fakeSequencer.expectNoMsg(3.seconds) // wait for connections

      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

      fakeSequencer.send(tla, Submit(sca))

      // This first one is the accept/verification
      val acceptedMsg = fakeSequencer.expectMsgClass(3.seconds, classOf[CommandResult])
      acceptedMsg.overall shouldBe Accepted

      val completeMsg = fakeSequencer.expectMsgClass(3.seconds, classOf[CommandResult])
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.status(0) shouldBe Completed
      // Wait a bit to see if there is any spurious messages
      fakeSequencer.expectNoMsg(250.milli)
      info("Msg: " + completeMsg)
      cleanup(tla)
    }

    it("should allow a datum then a set of positions as separate sca") {
      val tla = newTrombone()
      val fakeSequencer = TestProbe()

      tla ! SubscribeLifecycleCallback(fakeSequencer.ref)
      fakeSequencer.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeSequencer.expectMsg(20.seconds, LifecycleStateChanged(LifecycleRunning))

      //fakeSequencer.expectNoMsg(12.seconds)  // wait for connections

      val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
      fakeSequencer.send(tla, Submit(datum))

      // This first one is the accept/verification
      var acceptedMsg = fakeSequencer.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones
      var completeMsg = fakeSequencer.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted

      // This will send a config arg with 10 position commands
      val testRangeDistance = 90 to 180 by 10
      val positionConfigs = testRangeDistance.map(f => positionSC(f))

      val sca = Configurations.createSetupConfigArg("testobsId", positionConfigs: _*)
      fakeSequencer.send(tla, Submit(sca))

      // This first one is the accept/verification
      acceptedMsg = fakeSequencer.expectMsgClass(3.seconds, classOf[CommandResult])
      logger.info("msg1: " + acceptedMsg)
      acceptedMsg.overall shouldBe Accepted

      // Second one is completion of the executed ones - give this some extra time to complete
      completeMsg = fakeSequencer.expectMsgClass(10.seconds, classOf[CommandResult])
      logger.info("msg2: " + completeMsg)
      completeMsg.overall shouldBe AllCompleted
      completeMsg.details.results.size shouldBe sca.configs.size
      cleanup(tla)
    }
  }

}
