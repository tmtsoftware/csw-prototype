package csw.services.pkg

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.services.loc.LocationService
import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister, HcdInfo}
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

  def newHcdSupervisor(implicit system: ActorSystem, component: ActorRef): TestSupervisor = {
    import scala.concurrent.duration._

    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.SimpleTestHcd"

    val hcdInfo = HcdInfo(name, prefix, className, DoNotRegister, Set.empty, 1.second)

    val props = Supervisor.props(hcdInfo, Some(component))

    TestActorRef(props)
  }

  /**
   * This function encpsulates the protocol when iniitalization succeeds to running state
   * @param supervisor supervisor test actor
   * @param component component test probe
   * @return
   */
  private def successfulInitialize(supervisor: TestActorRef[Supervisor], component: TestProbe): Unit = {
    supervisor.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    component.send(supervisor, Initialized)

    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    component.expectMsg(Running)
  }

  /**
   * This function encpsulates the protocol when iniitalization fails
   * @param supervisor supervisor test actor
   * @param component component test probe
   */
  private def failedInitialize(supervisor: TestSupervisor, component: TestProbe): Unit = {
    supervisor.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    component.send(supervisor, InitializeFailure("Failure"))

    val failReason = "Failure"

    supervisor.underlyingActor.lifecycleState should be(LifecycleInitializeFailure)

    component.expectMsg(LifecycleFailureInfo(LifecycleWaitingForInitialized, failReason))
  }

  /**
   * Tests protocol between supervisor and commponent for successful initialization to Running
   */
  it("in pendingInitialize should accept Initialized with Success") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    successfulInitialize(supervisor, component)
    cleanup(Some(supervisor))
  }

  /**
   * Tests protocol between supervisor and component when component fails initialization
   */
  it("in pendingInitialize should handle failure to Initialize") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    failedInitialize(supervisor, component)
    cleanup(Some(supervisor))
  }

  /**
   * Tests that external messages get through to the component when online/running and running offline
   */
  it("Should get messages through when running and runningOffline") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    successfulInitialize(supervisor, component)
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    // Check message in LifecycleRunnning
    supervisor ! "msg"
    component.expectMsg("msg")

    successfulOnlineToOffline(supervisor, component)

    // Check message gets through in offline state
    supervisor ! "msg2"
    component.expectMsg("msg2")

    cleanup(Some(supervisor))
  }

  /**
   * This test checks the protocol for shutdown.
   * A component can start the shutdown itself by sending HaltComponent to supervisor
   */
  it("should allow halting") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    successfulInitialize(supervisor, component)
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    // Request halt
    component.send(supervisor, HaltComponent)

    // Normal shutdown message from supervisor and component response
    component.expectMsg(DoShutdown)
    component.send(supervisor, ShutdownComplete)

    cleanup(Some(supervisor))
  }

  // Check outside requests
  /**
   * This test checks that restart is handled from the outside when in Running state
   */
  it("should allow restart from outside") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    // When in LifecycleWaitingForInitialized external messages should be ignored
    supervisor ! ExComponentRestart
    // No change in LifecycleWaitingForInitialized
    supervisor.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)
    component.expectNoMsg()

    // Now move to Running
    successfulInitialize(supervisor, component)
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    // Handle outside restart - supervisor goes back to waiting for inialized, and component gets message DoRestart
    supervisor ! ExComponentRestart
    supervisor.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)
    component.expectMsg(DoRestart)

    cleanup(Some(supervisor))
  }

  /**
   * This tests if a component in Running state can go between offline and online
   */
  it("should allow toggling between online and offline") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    supervisor ! ExComponentOnline
    // No change
    supervisor.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)
    component.expectNoMsg()

    // Now go to running state
    successfulInitialize(supervisor, component)
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    // When sending online, it should stay in running state
    supervisor ! ExComponentOnline
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    // When getting offline, moves to RunningOffline and component gets message
    successfulOnlineToOffline(supervisor, component)

    // Check for repeat message
    supervisor ! ExComponentOffline
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunningOffline)
    component.expectNoMsg()

    // When offline and online received, it goes to Running and component gets a message
    successfulOfflineToOnline(supervisor, component)

    cleanup(Some(supervisor))
  }

  /**
   * This function encapsulates supervisor and component interaction when successfully being requested to go
   * from offline to online by an external actor
   * @param supervisor supervisor actor
   * @param component component test probe
   */
  private def successfulOnlineToOffline(supervisor: TestSupervisor, component: TestProbe): Unit = {
    supervisor ! ExComponentOffline
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunningOffline)

    component.expectMsg(RunningOffline)
  }

  /**
   * This function encapsulates supervisor and component interaction when successfully being requested to go
   * from offline to running/online by an external actor
   * @param supervisor supervisor actor
   * @param component component test probe
   */
  private def successfulOfflineToOnline(supervisor: TestSupervisor, component: TestProbe): Unit = {
    supervisor ! ExComponentOnline
    // No change
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    component.expectMsg(Running)
  }

  // XXX TODO FIXME: THis test was failing on CentOS-7
  //   Check outside requests
  it("should allow shutdown from outside from LifecycleRunningOffline") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    // Check for no change before initialization
    supervisor ! ExComponentShutdown
    // No change
    supervisor.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)

    // Initilialize component
    successfulInitialize(supervisor, component)
    supervisor.underlyingActor.lifecycleState should be(LifecycleRunning)

    successfulExShutdownFromOnline(supervisor, component)
    supervisor.underlyingActor.lifecycleState should be(LifecycleShutdown)

    cleanup(Some(supervisor))
  }

  /**
   * This function encapsulates the protocol between supervisor and compoent when an external shutdown request is made
   * @param supervisor test supervisor
   * @param component component test probe
   */
  private def successfulExShutdownFromOnline(supervisor: TestSupervisor, component: TestProbe): Unit = {
    supervisor ! ExComponentShutdown
    supervisor.underlyingActor.lifecycleState should be(LifecyclePreparingToShutdown)

    component.expectMsg(DoShutdown)

    supervisor ! ShutdownComplete
    supervisor.underlyingActor.lifecycleState should be(LifecycleShutdown)
  }

  /**
   * This tests the receiving of lifecycle events by an actor outside/external to the supervisor
   */
  it("Should get normal events for normal startup") {
    val component = TestProbe()

    // This is the external entity that listens for lifecycle events from supervisor
    val stateProbe = TestProbe()

    val supervisor = newHcdSupervisor(system, component.ref)
    // External subscribes to lifecycle events
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    // Component Initializes
    component.send(supervisor, Initialized)
    // Component receives Running
    component.expectMsg(Running)

    // External gets LifecycleRunning event when component is running
    stateProbe.expectMsg(LifecycleStateChanged(LifecycleRunning))
    // checking for no other messages
    stateProbe.expectNoMsg(500.milli)

    // All messages pass through in running mode
    supervisor ! "test"
    component.expectMsg("test")

    // Component startup complete
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)

    //system.stop(supervisor)
    cleanup(Some(supervisor))
  }

  /**
   * This tests external events from supervisor when external sends Shutdown to supervisor
   */
  it("Should get normal events for shutdown") {
    val component = TestProbe()

    // External subscribes to lifecycle events
    val stateProbe = TestProbe()

    val supervisor = newHcdSupervisor(system, component.ref)
    info(s"Supervisor: $supervisor")

    // Component Initializes
    component.send(supervisor, Initialized)
    component.expectMsg(Running)

    stateProbe.expectNoMsg(500.milli)

    // Start watching for shutdown
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    // external sends shutdown request
    stateProbe.send(supervisor, ExComponentShutdown)

    // Response to shutdown
    component.expectMsg(DoShutdown)
    stateProbe.expectMsg(LifecycleStateChanged(LifecyclePreparingToShutdown))

    // Component sends that it is ready to shutdown
    component.send(supervisor, ShutdownComplete)

    // Supervisor updates external
    stateProbe.expectMsg(LifecycleStateChanged(LifecycleShutdown))

    // Component startup complete
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)

    //system.stop(supervisor)
    cleanup(Some(supervisor))
  }

  //  private def successfulExRestartFromOnline(fsm: TestSupervisor, component: TestProbe) = {
  //    fsm ! ExComponentRestart
  //    fsm.underlyingActor.lifecycleState should be(LifecycleWaitingForInitialized)
  //
  //    component.expectMsg(DoRestart)
  //  }

  it("shutdown should timeout after 5 seconds when no callback") {
    val component = TestProbe()
    val supervisor = newHcdSupervisor(system, component.ref)

    // External subscribes to lifecycle events
    val stateProbe = TestProbe()

    // State probe/external subscribes to events
    stateProbe.send(supervisor, SubscribeLifecycleCallback(stateProbe.ref))

    // Component initializes and goes to running
    successfulInitialize(supervisor, component)

    // External is updated
    var msg = stateProbe.expectMsg(LifecycleStateChanged(LifecycleRunning))
    // Check for no messages
    component.expectNoMsg(200.milliseconds)
    stateProbe.expectNoMsg(200.milliseconds)

    // Start the shutdown and timeout from external
    stateProbe.send(supervisor, ExComponentShutdown)

    // Component gets message to DoShutdown
    component.expectMsg(DoShutdown)

    // Transition to waiting for shutdown
    msg = stateProbe.expectMsg(LifecycleStateChanged(LifecyclePreparingToShutdown))
    // Check for no messages
    component.expectNoMsg(200.milliseconds)
    stateProbe.expectNoMsg(200.milliseconds)

    // Now timeout for failure to respond 5 seconds + 1
    msg = stateProbe.expectMsg(6.seconds, LifecycleStateChanged(LifecycleShutdownFailure))

    // Component will get alerted of shutdown failure while transitioning to final state
    component.expectMsgClass(classOf[LifecycleFailureInfo])

    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)
    cleanup(Some(supervisor))
  }

}
