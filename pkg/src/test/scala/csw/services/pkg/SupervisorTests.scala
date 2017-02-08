package csw.services.pkg

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.services.loc.LocationService
import csw.services.pkg.Component.{AssemblyInfo, HcdInfo, RegisterOnly}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.duration._

object SupervisorTests {
  LocationService.initInterface()
  val system = ActorSystem("SupervisorTests")
}

case class SimpleTestHcd(override val info: HcdInfo, supervisor: ActorRef) extends Hcd {
  def receive: Receive = Actor.emptyBehavior
}

case class SimpleTestAssembly(override val info: AssemblyInfo, supervisor: ActorRef) extends Assembly {
  def receive: Receive = Actor.emptyBehavior
}

class SupervisorTests() extends TestKit(SupervisorTests.system) with ImplicitSender
    with FunSpecLike with Matchers with BeforeAndAfterAll {

  import SupervisorExternal._

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  import Supervisor._

  type TestSupervisor = TestActorRef[Supervisor]

  // Stop any actors created for a test to avoid conflict with other tests
  private def cleanup(hcd: Option[ActorRef], a: ActorRef*): Unit = {
    val monitor = TestProbe()
    a.foreach { actorRef =>
      monitor.watch(actorRef)
      system.stop(actorRef)
      monitor.expectTerminated(actorRef)
    }

    hcd.foreach { hcd =>
      monitor.watch(hcd)
      //      hcd ! HaltComponent
      hcd ! PoisonPill
      monitor.expectTerminated(hcd)
    }
  }

  def newHcdFSM(implicit system: ActorSystem, component: ActorRef): TestSupervisor = {
    import scala.concurrent.duration._

    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.SimpleTestHcd"

    val hcdInfo = HcdInfo(name, prefix, className, RegisterOnly, Set.empty, 1.second)

    val props = Supervisor.props(hcdInfo, Some(component))

    TestActorRef(props)
  }

  //  it("should be initialized in the Pending Initialized State") {
  //    val name = "test1"
  //    val prefix = "test1.prefix"
  //    val className = "csw.services.pkg.SimpleTestHcd"
  //
  //    val hcdInfo = HcdInfo(name, prefix, className, RegisterOnly, Set.empty, 1.second)
  //
  //    val fsm = TestActorRef(Supervisor.props(hcdInfo))
  //  }

  it("in pendingInitialize should accept Initialized with Success") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    successfulInitialize(fsm, component)
    cleanup(Some(fsm))
  }

  private def successfulInitialize(fsm: TestActorRef[Supervisor], component: TestProbe) = {
    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    component.send(fsm, Initialized)

    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)
  }

  it("in pendingInitialize should handle failure to Initialize") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    failedInitialize(fsm, component)
    cleanup(Some(fsm))
  }

  it("successful sequential Initialized and Startup") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)
    cleanup(Some(fsm))
  }

  it("should fail properly from initalize to startup") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    successfulInitialize(fsm, component)
    failedStartupFromInitialize(fsm, component)
    cleanup(Some(fsm))
  }

  private def failedInitialize(fsm: TestSupervisor, component: TestProbe) = {
    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    component.send(fsm, InitializeFailure("Failure"))

    val failReason = "Failure"

    fsm.underlyingActor.lifecycleState should be(LifecycleFailure)
    //fsm.stateData should equal(FailureInfo(LifecycleWaitingForInitialized, failReason))

    component.expectMsg(LifecycleFailureInfo(LifecycleWaitingForInitialized, failReason))
  }

  private def successfulStartupFromInitilzed(fsm: TestSupervisor, component: TestProbe) = {
    // First ensure we are in Initialized state
    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)

    component.send(fsm, Started)

    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    component.expectMsg(Running)
  }

  private def failedStartupFromInitialize(fsm: TestSupervisor, component: TestProbe) = {
    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)

    val failReason = "StartupFailure"

    component.send(fsm, StartupFailure(failReason))

    fsm.underlyingActor.lifecycleState should be(LifecycleFailure)

    component.expectMsg(LifecycleFailureInfo(LifecycleInitialized, failReason))
  }

  // Check outside requests
  it("should allow restart from outside") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    fsm ! ExComponentRestart
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    successfulInitialize(fsm, component)

    fsm ! ExComponentRestart
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)

    successfulStartupFromInitilzed(fsm, component)

    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    successfulRestartFromRunning(fsm, component)
    cleanup(Some(fsm))
  }

  private def successfulRestartFromRunning(fsm: TestSupervisor, component: TestProbe) = {
    fsm ! ExComponentRestart

    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    component.expectMsg(DoRestart)
  }

  it("should allow toggling between online and offline") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    fsm ! ExComponentOnline
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    fsm ! ExComponentOffline
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    successfulInitialize(fsm, component)

    fsm ! ExComponentOnline
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)

    fsm ! ExComponentOffline
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)

    successfulStartupFromInitilzed(fsm, component)
    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    fsm ! ExComponentOnline
    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    fsm ! ExComponentOffline
    fsm.underlyingActor.lifecycleState should be(LifecycleRunningOffline)

    component.expectMsg(RunningOffline)

    fsm ! ExComponentOnline
    // No change
    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    component.expectMsg(Running)
    cleanup(Some(fsm))
  }

  // XXX TODO FIXME: THis test was failing on CentOS-7
  //   Check outside requests
  //  it("should allow shutdown from outside from LifecycleRunningOffline") {
  //    val component = TestProbe()
  //    val fsm = newHcdFSM(system, component.ref)
  //
  //    fsm ! ExComponentShutdown
  //    // No change
  //    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)
  //
  //    successfulInitialize(fsm, component)
  //
  //    fsm ! ExComponentShutdown
  //    // No change
  //    fsm.underlyingActor.lifecycleState should be(LifecycleInitialized)
  //
  //    successfulStartupFromInitilzed(fsm, component)
  //    // No change
  //    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)
  //
  //    fsm ! ExComponentOffline
  //
  //    fsm.underlyingActor.lifecycleState should be(LifecycleRunningOffline)
  //
  //    component.expectMsg(RunningOffline)
  //
  //    successfulExShutdownFromOnline(fsm, component)
  //    fsm.underlyingActor.lifecycleState should be(LifecycleShutdown)
  //    system.stop(fsm)
  //    //      cleanup(Some(fsm))
  //  }

  private def successfulExShutdownFromOnline(fsm: TestSupervisor, component: TestProbe) = {
    fsm ! ExComponentShutdown
    fsm.underlyingActor.lifecycleState should be(LifecyclePreparingToShutdown)

    component.expectMsg(DoShutdown)

    fsm ! ShutdownComplete
    fsm.underlyingActor.lifecycleState should be(LifecycleShutdown)
  }

  it("Should get normal events for normal startup") {
    val component = TestProbe()

    val stateProbe = TestProbe()

    val supervisor = newHcdFSM(system, component.ref)
    info(s"Supervisor: $supervisor")

    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    // Component Initializes
    component.send(supervisor, Initialized)

    var msg = stateProbe.expectMsg(LifecycleStateChanged(LifecycleInitialized))

    stateProbe.expectNoMsg(500.milli)

    component.send(supervisor, Started)

    msg = stateProbe.expectMsg(LifecycleStateChanged(LifecycleRunning))

    component.expectMsg(Running)

    stateProbe.expectNoMsg(500.milli)

    // All messages pass through in running mode
    supervisor ! "test"
    component.expectMsg("test")

    // Component startup complete
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)

    //system.stop(supervisor)
    cleanup(Some(supervisor))
  }

  it("Should get normal events for shutdown") {
    val component = TestProbe()

    val stateProbe = TestProbe()

    val supervisor = newHcdFSM(system, component.ref)
    info(s"Supervisor: $supervisor")
    // Component Initializes
    component.send(supervisor, Initialized)
    component.send(supervisor, Started)
    component.expectMsg(Running)

    stateProbe.expectNoMsg(500.milli)

    // Start watching for shutdown
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    supervisor ! ExComponentShutdown

    component.expectMsg(DoShutdown)

    stateProbe.expectMsg(LifecycleStateChanged(LifecyclePreparingToShutdown))

    component.send(supervisor, ShutdownComplete)

    stateProbe.expectMsg(LifecycleStateChanged(LifecycleShutdown))

    // Component startup complete
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)

    //system.stop(supervisor)
    cleanup(Some(supervisor))
  }

  private def successfulOfflineFromOnline(fsm: TestSupervisor, component: TestProbe) = {
    fsm ! ExComponentOffline
    fsm.underlyingActor.lifecycleState should be(LifecycleRunningOffline)

    component.expectMsg(RunningOffline)
  }

  //  private def successfulExRestartFromOnline(fsm: TestSupervisor, component: TestProbe) = {
  //    fsm ! ExComponentRestart
  //    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)
  //
  //    component.expectMsg(DoRestart)
  //  }

  //  private def successfulOnlineFromOffline(fsm: TestSupervisor, component: TestProbe) = {
  //    fsm ! ExComponentOnline
  //    // No change
  //    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)
  //
  //    component.expectMsg(Running)
  //  }

  it("shutdown should timeout after 5 seconds when no callback") {

    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    val stateProbe = TestProbe()

    fsm ! SubscribeLifecycleCallback(stateProbe.ref)

    successfulInitialize(fsm, component)

    // This transition is going from Waiting to Initialized
    var msg = stateProbe.expectMsg(LifecycleStateChanged(LifecycleInitialized))
    component.expectNoMsg(200.milliseconds)
    stateProbe.expectNoMsg(200.milliseconds)

    successfulStartupFromInitilzed(fsm, component)
    msg = stateProbe.expectMsg(LifecycleStateChanged(LifecycleRunning))
    component.expectNoMsg(200.milliseconds)
    stateProbe.expectNoMsg(200.milliseconds)

    // Start the shutdown and timeout
    fsm ! ExComponentShutdown
    // Component gets message to DoShutdown
    component.expectMsg(DoShutdown)

    // Transition to waiting for shutdown
    //stateProbe.expectMsg(Transition(fsm, LifecycleRunning, LifecyclePreparingToShutdown))
    msg = stateProbe.expectMsg(LifecycleStateChanged(LifecyclePreparingToShutdown))
    component.expectNoMsg(200.milliseconds)
    stateProbe.expectNoMsg(200.milliseconds)

    // Now timeout for failure to respond 5 seconds + 1
    msg = stateProbe.expectMsg(6.seconds, LifecycleStateChanged(LifecycleShutdownFailure))

    // Component will get alerted of shutdown failure while transitioning to final state
    component.expectMsgClass(classOf[LifecycleFailureInfo])

    fsm ! UnsubscribeLifecycleCallback(stateProbe.ref)
    cleanup(Some(fsm))
  }

  it("Should get messages through when running and runningOffline") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)

    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    fsm ! "msg"
    component.expectMsg("msg")

    successfulOfflineFromOnline(fsm, component)

    fsm ! "msg2"
    component.expectMsg("msg2")
    cleanup(Some(fsm))
  }

  it("should allow halting") {
    val component = TestProbe()
    val fsm = newHcdFSM(system, component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)

    fsm.underlyingActor.lifecycleState should be(LifecycleRunning)

    component.send(fsm, HaltComponent)

    component.expectMsg(DoShutdown)
    component.send(fsm, ShutdownComplete)

    expectNoMsg(3.seconds)
  }

}
