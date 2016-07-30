package csw.services.pkg

import akka.actor.FSM.{CurrentState, UnsubscribeTransitionCallBack}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, MustMatchers}


class LifecycleManager2Tests() extends TestKit(ActorSystem()) with ImplicitSender
  with FunSpecLike with MustMatchers with BeforeAndAfterAll {

  override def afterAll = TestKit.shutdownActorSystem(system)

  import LifecycleManager2._

  type MyTestFsm = TestFSMRef[LifecycleManager2.LifecycleState2, LifecycleManager2.ComponentState, LifecycleManager2]

  def nilActor = TestProbe().ref

  def newFSM(component: ActorRef = nilActor): MyTestFsm = {
    TestFSMRef(new LifecycleManager2(component, "test"))
  }

  def successfulInitialize(fsm: MyTestFsm, component: TestProbe) = {
    component.send(fsm, Initialized)

    fsm.stateName must be(LifecycleInitialized)
    fsm.stateData must be(ComponentInitialized)
  }

  def failedInitialize(fsm: MyTestFsm, component: TestProbe) = {
    fsm.stateName must be(LifecycleWaitingForInitialized)
    fsm.stateData must be(ComponentInitializing)

    component.send(fsm, InitializeFailure("Failure"))

    val failReason = "Failure"

    fsm.stateName must be(LifecycleFailure)
    fsm.stateData must equal(FailureInfo(LifecycleWaitingForInitialized, failReason))

    component.expectMsg(LifecycleFailure(LifecycleWaitingForInitialized, failReason))
  }

  def successfulStartupFromInitilzed(fsm: MyTestFsm, component: TestProbe) = {
    // First ensure we are in Initialized state
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)

    component.send(fsm, Started)

    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    component.expectMsg(Running)
  }

  def failedStartupFromInitialize(fsm: MyTestFsm, component: TestProbe) = {
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)

    val failReason = "StartupFailure"

    component.send(fsm, StartupFailure(failReason))

    assert(fsm.stateName === LifecycleFailure)
    assert(fsm.stateData === FailureInfo(LifecycleInitialized, failReason))

    component.expectMsg(LifecycleFailure(LifecycleInitialized, failReason))
  }

  def successfulRestartFromRunning(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! ExComponentRestart
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)

    component.expectMsg(DoRestart)
  }

  def successfulOfflineFromOnline(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! ExComponentOffline
    assert(fsm.stateName === LifecycleRunningOffline)
    assert(fsm.stateData === ComponentRunningOffline)

    component.expectMsg(RunningOffline)
  }

  def successfulOnlineFromOffline(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! ExComponentOnline
    // No change
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    component.expectMsg(Running)
  }

  def successfulExRestartFromOnline(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! ExComponentRestart
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)

    component.expectMsg(DoRestart)
  }

  def successfulExShutdownFromOnline(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! ExComponentShutdown
    assert(fsm.stateName === LifecycleWaitingForShutdown)
    assert(fsm.stateData === ComponentShuttingDown)

    component.expectMsg(DoShutdown)

    fsm ! ShutdownComplete
    assert(fsm.stateName == LifecycleShutdown)
    assert(fsm.stateData == ComponentShutDown)
  }

  it("should be initialized in the Pending Initialized State") {
    val fsm = newFSM()

    fsm.stateName must be(LifecycleWaitingForInitialized)
    fsm.stateData must be(ComponentInitializing)
  }

  it("In pendingInitialize should accept Initialized with Success") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
  }

  it("In pendingInitialize should handle failure to Initialize") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    failedInitialize(fsm, component)
  }

  it("Successful sequential Initialized and Startup") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)
  }

  it("Should fail properly from initalize to startup") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    failedStartupFromInitialize(fsm, component)
  }

  // Check outside requests
  it("Should allow restart from outside") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    fsm ! ExComponentRestart
    // No change
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)

    successfulInitialize(fsm, component)

    fsm ! ExComponentRestart
    // No change
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)

    successfulStartupFromInitilzed(fsm, component)
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    successfulRestartFromRunning(fsm, component)
  }

  it("Should allow toggling between online and offline") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    fsm ! ExComponentOnline
    // No change
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)
    fsm ! ExComponentOffline
    // No change
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)

    successfulInitialize(fsm, component)

    fsm ! ExComponentOnline
    // No change
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)
    fsm ! ExComponentOffline
    // No change
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)

    successfulStartupFromInitilzed(fsm, component)
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    fsm ! ExComponentOnline
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    fsm ! ExComponentOffline
    assert(fsm.stateName === LifecycleRunningOffline)
    assert(fsm.stateData === ComponentRunningOffline)

    component.expectMsg(RunningOffline)

    fsm ! ExComponentOnline
    // No change
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    component.expectMsg(Running)
  }

  // Check outside requests
  it("Should allow shutdown from outside from LifecycleRunning") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    fsm ! ExComponentShutdown
    // No change
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)

    successfulInitialize(fsm, component)

    fsm ! ExComponentShutdown
    // No change
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)

    successfulStartupFromInitilzed(fsm, component)
    // No change
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    successfulExShutdownFromOnline(fsm, component)
    assert(fsm.stateName == LifecycleShutdown)
    assert(fsm.stateData == ComponentShutDown)
  }

  // Check outside requests
  it("Should allow shutdown from outside from LifecycleRunningOffline") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    fsm ! ExComponentShutdown
    // No change
    assert(fsm.stateName === LifecycleWaitingForInitialized)
    assert(fsm.stateData === ComponentInitializing)

    successfulInitialize(fsm, component)

    fsm ! ExComponentShutdown
    // No change
    assert(fsm.stateName === LifecycleInitialized)
    assert(fsm.stateData === ComponentInitialized)

    successfulStartupFromInitilzed(fsm, component)
    // No change
    assert(fsm.stateName === LifecycleRunning)
    assert(fsm.stateData === ComponentRunningOnline)

    fsm ! ExComponentOffline
    assert(fsm.stateName === LifecycleRunningOffline)
    assert(fsm.stateData === ComponentRunningOffline)

    component.expectMsg(RunningOffline)

    successfulExShutdownFromOnline(fsm, component)
    assert(fsm.stateName == LifecycleShutdown)
    assert(fsm.stateData == ComponentShutDown)
  }

  it("Should allow subscribing to callbacks for supervisor") {
    import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    // val x = TargetInitialized
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    val fakesupervisor = TestProbe()

    fsm ! SubscribeTransitionCallBack(fakesupervisor.ref)

    fakesupervisor.expectMsg(CurrentState(fsm, LifecycleWaitingForInitialized))

    successfulInitialize(fsm, component)

    // This transition is going from Waiting to
    fakesupervisor.expectMsg(Transition(fsm, LifecycleWaitingForInitialized, LifecycleInitialized))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleInitialized, LifecycleInitialized))

    successfulStartupFromInitilzed(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleInitialized, LifecycleRunning))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleRunning))

    successfulOfflineFromOnline(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleRunningOffline))

    successfulOnlineFromOffline(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunningOffline, LifecycleRunning))

    successfulExRestartFromOnline(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleWaitingForInitialized))

    // Bring back to running and then shutdown
    successfulInitialize(fsm, component)

    // This transition is going from Waiting to Initialized
    fakesupervisor.expectMsg(Transition(fsm, LifecycleWaitingForInitialized, LifecycleInitialized))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleInitialized, LifecycleInitialized))

    successfulStartupFromInitilzed(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleInitialized, LifecycleRunning))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleRunning))

    successfulExShutdownFromOnline(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleWaitingForShutdown))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleWaitingForShutdown, LifecycleShutdown))

    fsm ! UnsubscribeTransitionCallBack(fakesupervisor.ref)
  }

  it("Should allow subscribing to callbacks for test actor") {
    import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    val component = TestProbe()
    val fsm = newFSM(component.ref)
    var setup = false // Set to true if received initial state
    var waitingToInitalized = false // Set when initialized is received due to transition
    var reachedInitialized = false // Set to true if successful

    val transitionTester = system.actorOf(Props(new Actor {
      def receive = {
        case t@Transition(_, LifecycleWaitingForInitialized, LifecycleInitialized) =>
          waitingToInitalized = true
        case Transition(_, LifecycleInitialized, LifecycleInitialized) =>
          reachedInitialized = true
        case s@CurrentState(_, _) =>
          assert(s === CurrentState(fsm, LifecycleWaitingForInitialized))
          setup = true
      }
    }))

    fsm ! SubscribeTransitionCallBack(transitionTester)

    successfulInitialize(fsm, component)

    // This is needed for some reason to allow success below when running all tests together.
    // I beleve it is because events take time but test proceeds
    Thread.sleep(2)
    // Check
    assert(setup)
    assert(waitingToInitalized)
    assert(reachedInitialized)

    fsm ! UnsubscribeTransitionCallBack(transitionTester)
  }

  it("Shutdown should timeout after 5 seconds when no callback") {
    import akka.actor.FSM.{SubscribeTransitionCallBack, Transition}

    val component = TestProbe()
    val fsm = newFSM(component.ref)

    val fakesupervisor = TestProbe()

    fsm ! SubscribeTransitionCallBack(fakesupervisor.ref)

    fakesupervisor.expectMsg(CurrentState(fsm, LifecycleWaitingForInitialized))

    successfulInitialize(fsm, component)

    // This transition is going from Waiting to Init
    fakesupervisor.expectMsg(Transition(fsm, LifecycleWaitingForInitialized, LifecycleInitialized))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleInitialized, LifecycleInitialized))

    successfulStartupFromInitilzed(fsm, component)
    fakesupervisor.expectMsg(Transition(fsm, LifecycleInitialized, LifecycleRunning))
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleRunning))

    import scala.concurrent.duration._

    // Start the shutdown and timeout
    fsm ! ExComponentShutdown
    // Component gets message to DoShutdown
    component.expectMsg(DoShutdown)

    // Transition to waiting for shutdown
    fakesupervisor.expectMsg(Transition(fsm, LifecycleRunning, LifecycleWaitingForShutdown))


    // Now timeout for failure to respond 5 seconds + 1
    fakesupervisor.expectMsg(6.seconds, new Transition(fsm, LifecycleWaitingForShutdown, LifecycleShutdownFailure))

    // Component will get alerted of shutdown failure while transitioning to final state
    component.expectMsgClass(classOf[LifecycleFailure])

    fsm ! UnsubscribeTransitionCallBack(fakesupervisor.ref)
  }

}
