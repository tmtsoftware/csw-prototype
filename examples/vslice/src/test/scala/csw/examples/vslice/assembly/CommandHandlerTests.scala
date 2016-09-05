package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.examples.vslice.assembly.TromboneCommandHandler.ExecSequential
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.CommandStatus2.ExecResults
import csw.services.ccs.CurrentStateReceiver
import csw.services.ccs.CurrentStateReceiver.AddPublisher
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentId, ComponentType, TestLocationService}
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor3
import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
import csw.util.config.{Configurations, RunId}
import csw.util.config.Configurations.SetupConfig
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}

import scala.language.reflectiveCalls
import scala.concurrent.duration._
/**
  * TMT Source Code: 8/26/16.
  */
class CommandHandlerTests extends TestKit(ActorSystem("TromboneAssemblyCommandHandlerTests")) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {

  import TromboneAssembly._
  import csw.services.loc.TestLocationService._


  def startHCD: ActorRef = {
    val testInfo = HcdInfo(TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second)

    Supervisor3(testInfo)
  }

  override def afterAll = TestKit.shutdownActorSystem(system)

  val hcdCompID = ComponentId(TromboneHCD.componentName, ComponentType.HCD)
  val hcdConnection = AkkaConnection(hcdCompID)

  def fixture =
    new {
      initInterface()
      val tromHCD = startHCD // TestProbe()
      val r1 = TestLocationService.registerAkkaConnection(hcdCompID, tromHCD, TromboneAssembly.componentPrefix)
    }

  describe("Basic setup tests") {
    import TromboneStateHandler._

    it("should allow running datum") {
      val f = fixture
      val tromboneHCD = f.tromHCD
      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      val currentStateReceiver = system.actorOf(CurrentStateReceiver.props)
      currentStateReceiver ! AddPublisher(tromboneHCD)


      val ch: TestActorRef[TromboneCommandHandler] = TestActorRef(TromboneCommandHandler.props(Set(hcdConnection), currentStateReceiver, Some(fakeAssembly.ref)))

      ch ! TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false) )

      println("Its: " + ch.underlyingActor.connections)

      val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(datumCK))

      ch ! ExecSequential(sca, Some(f.tromHCD), Some(fakeAssembly.ref))


      val msg = fakeAssembly.expectMsgClass(35.seconds, classOf[ExecResults])
      println("Final: " + msg)

    }

    it("should allow a move") {
      val f = fixture
      val tromboneHCD = f.tromHCD
      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      val currentStateReceiver = system.actorOf(CurrentStateReceiver.props)
      currentStateReceiver ! AddPublisher(tromboneHCD)

      val ch: TestActorRef[TromboneCommandHandler] = TestActorRef(TromboneCommandHandler.props(Set(hcdConnection), currentStateReceiver, Some(fakeAssembly.ref)))


      val sca = Configurations.createSetupConfigArg("testobsId", moveSC(500))

      ch ! ExecSequential(sca, Some(f.tromHCD), Some(fakeAssembly.ref))


      val msg = fakeAssembly.expectMsgClass(35.seconds, classOf[ExecResults])
      println("Final: " + msg)
    }

    it("should allow two moves") {
      val f = fixture
      val tromboneHCD = f.tromHCD
      val fakeAssembly = TestProbe()

      // The following is to synchronize the test with the HCD entering Running state
      // This is boiler plate for setting up an HCD for testing
      tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
      fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
      //info("Running")

      val currentStateReceiver = system.actorOf(CurrentStateReceiver.props)
      currentStateReceiver ! AddPublisher(tromboneHCD)

      val ch: TestActorRef[TromboneCommandHandler] = TestActorRef(TromboneCommandHandler.props(Set(hcdConnection), currentStateReceiver, Some(fakeAssembly.ref)))


      val sca = Configurations.createSetupConfigArg("testobsId", moveSC(500), moveSC(700))

      ch ! ExecSequential(sca, Some(f.tromHCD), Some(fakeAssembly.ref))


      val msg = fakeAssembly.expectMsgClass(35.seconds, classOf[ExecResults])
      logger.info("Final: " + msg)
    }

  }

}
