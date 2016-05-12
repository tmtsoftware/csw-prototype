package csw.services.pkg

import akka.actor.FSM.UnsubscribeTransitionCallBack
import akka.actor._
import akka.testkit._
import csw.services.pkg.LifecycleHandler.{Failure, HandlerResponse}
import csw.services.pkg.LifecycleManager._
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, MustMatchers}

import scala.language.postfixOps

abstract class FSMSpec extends TestKit(ActorSystem()) with ImplicitSender
    with FunSpecLike with MustMatchers with BeforeAndAfterAll {
  override def afterAll = TestKit.shutdownActorSystem(system)
}

class LifecycleManagerTest() extends FSMSpec {

  type MyTestFsm = TestFSMRef[LifecycleManager.LifecycleState, LifecycleManager.FSMData, LifecycleManager]

  def nilActor = TestProbe().ref

  def newFSM(component: ActorRef = nilActor): MyTestFsm = {
    TestFSMRef(new LifecycleManager(component, "test"))
  }

  it("should be initialized in the Loaded State") {
    val fsm = newFSM()

    assert(fsm.stateName === Loaded)
    assert(fsm.stateData === TargetLoaded)
  }

  def successfulInitialize(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! Initialize

    assert(fsm.stateName === PendingInitializedFromLoaded)
    assert(fsm.stateData === TargetInitialized)

    component.expectMsg(Initialize)
    component.send(fsm, InitializeSuccess)

    assert(fsm.stateName === Initialized)
    assert(fsm.stateData === TargetInitialized)
  }

  def failedInitialize(fsm: MyTestFsm, component: TestProbe) = {
    fsm ! Initialize

    assert(fsm.stateName === PendingInitializedFromLoaded)
    assert(fsm.stateData === TargetInitialized)

    component.expectMsg(Initialize)
    component.send(fsm, InitializeFailure("Failure"))

    val failReason = "Failure"

    assert(fsm.stateName === LifecycleFailure)
    assert(fsm.stateData === FailureInfo(Initialized, failReason))

    component.expectMsg(LifecycleFailure(Initialized, failReason))
  }

  def successfulStartupFromInitilzed(fsm: MyTestFsm, component: TestProbe) = {
    // First ensure we are in Initialized state
    assert(fsm.stateName === Initialized)
    assert(fsm.stateData === TargetInitialized)

    fsm ! Startup

    assert(fsm.stateName === PendingRunningFromInitialized)
    assert(fsm.stateData === TargetRunning)

    component.expectMsg(Startup)
    component.send(fsm, StartupSuccess)

    assert(fsm.stateName === Running)
    assert(fsm.stateData === TargetRunning)
  }

  def failedStartupFromInitialize(fsm: MyTestFsm, component: TestProbe) = {
    assert(fsm.stateName === Initialized)
    assert(fsm.stateData === TargetInitialized)

    fsm ! Startup

    assert(fsm.stateName === PendingRunningFromInitialized)
    assert(fsm.stateData === TargetRunning)

    val failReason = "StartupFailure"

    component.expectMsg(Startup)
    component.send(fsm, StartupFailure(failReason))

    assert(fsm.stateName === LifecycleFailure)
    assert(fsm.stateData === FailureInfo(Running, failReason))

    component.expectMsg(LifecycleFailure(Running, failReason))
  }

  def successfulShutdownFromRunning(fsm: MyTestFsm, component: TestProbe) = {
    assert(fsm.stateName == Running)
    assert(fsm.stateData == TargetRunning)

    fsm ! Shutdown

    assert(fsm.stateName == PendingInitializedFromRunning)
    assert(fsm.stateData == TargetInitialized)

    component.expectMsg(Shutdown)
    component.send(fsm, ShutdownSuccess)

    assert(fsm.stateName == Initialized)
    assert(fsm.stateData == TargetInitialized)
  }

  def failedShutdownFromRunning(fsm: MyTestFsm, component: TestProbe) = {
    assert(fsm.stateName === Running)
    assert(fsm.stateData === TargetRunning)

    fsm ! Shutdown

    assert(fsm.stateName === PendingInitializedFromRunning)
    assert(fsm.stateData === TargetInitialized)

    val failReason = "ShutdownFailure"

    component.expectMsg(Shutdown)
    component.send(fsm, ShutdownFailure(failReason))

    assert(fsm.stateName === LifecycleFailure)
    assert(fsm.stateData === FailureInfo(Initialized, failReason))

    component.expectMsg(LifecycleFailure(Initialized, failReason))
  }

  def successfulUninitializeFromInitalized(fsm: MyTestFsm, component: TestProbe) = {
    assert(fsm.stateName == Initialized)
    assert(fsm.stateData == TargetInitialized)

    fsm ! Uninitialize

    assert(fsm.stateName === PendingLoadedFromInitialized)
    assert(fsm.stateData === TargetLoaded)

    component.expectMsg(Uninitialize)
    component.send(fsm, UninitializeSuccess)

    assert(fsm.stateName === Loaded)
    assert(fsm.stateData === TargetLoaded)
  }

  def failedUninitializeFromInitialize(fsm: MyTestFsm, component: TestProbe) = {
    assert(fsm.stateName === Initialized)
    assert(fsm.stateData === TargetInitialized)

    fsm ! Uninitialize

    assert(fsm.stateName === PendingLoadedFromInitialized)
    assert(fsm.stateData === TargetLoaded)

    val failReason = "UninitializeFailure"

    component.expectMsg(Uninitialize)
    component.send(fsm, UninitializeFailure(failReason))

    assert(fsm.stateName === LifecycleFailure)
    assert(fsm.stateData === FailureInfo(Loaded, failReason))

    component.expectMsg(LifecycleFailure(Loaded, failReason))
  }

  it("In loaded should accept Initialize with Success") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
  }

  it("In loaded should handle failure to Initialize") {
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

  it("should accept Startup from Loaded and go directly through to Running") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    fsm ! Startup

    assert(fsm.stateName === PendingInitializedFromLoaded)
    assert(fsm.stateData === TargetRunning)

    component.expectMsg(Initialize)
    component.send(fsm, InitializeSuccess)

    assert(fsm.stateName === PendingRunningFromInitialized)
    assert(fsm.stateData === TargetRunning)

    component.expectMsg(Startup)
    component.send(fsm, StartupSuccess)

    assert(fsm.stateName === Running)
    assert(fsm.stateData === TargetRunning)
  }

  it("Should handle successful startup from Initialized") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)
  }

  it("Should fail properly during startup") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    failedStartupFromInitialize(fsm, component)
  }

  it("should accept Shutdown from Running to go to Initialized") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)

    successfulShutdownFromRunning(fsm, component)
  }

  it("should accept Shutdown from Running and fail properly") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)

    failedShutdownFromRunning(fsm, component)
  }

  it("should accept Uninitialize from Initialize to go to Loaded") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)
    successfulShutdownFromRunning(fsm, component)
    successfulUninitializeFromInitalized(fsm, component)
  }

  it("should work properly for failed Uninitialize from Initialize") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    failedUninitializeFromInitialize(fsm, component)
  }

  it("should accept Uninitialize from Running and go directly through to Loaded") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    successfulInitialize(fsm, component)
    successfulStartupFromInitilzed(fsm, component)

    assert(fsm.stateName === Running)
    assert(fsm.stateData === TargetRunning)

    fsm ! Uninitialize

    assert(fsm.stateName === PendingInitializedFromRunning)
    assert(fsm.stateData === TargetLoaded)

    component.expectMsg(Shutdown)
    component.send(fsm, ShutdownSuccess)

    assert(fsm.stateName == PendingLoadedFromInitialized)
    assert(fsm.stateData == TargetLoaded)

    component.expectMsg(Uninitialize)
    component.send(fsm, UninitializeSuccess)

    assert(fsm.stateName == Loaded)
    assert(fsm.stateData == TargetLoaded)
  }

  it("Should not be troubled receiving events for the state it is in") {
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    assert(fsm.stateName == Loaded)
    assert(fsm.stateData == TargetLoaded)

    fsm ! Load

    assert(fsm.stateName == Loaded)
    assert(fsm.stateData == TargetLoaded)

    successfulInitialize(fsm, component)

    assert(fsm.stateName == Initialized)
    assert(fsm.stateData == TargetInitialized)

    fsm ! Initialize

    assert(fsm.stateName == Initialized)
    assert(fsm.stateData == TargetInitialized)

    successfulStartupFromInitilzed(fsm, component)

    assert(fsm.stateName == Running)
    assert(fsm.stateData == TargetRunning)

    fsm ! Running

    assert(fsm.stateName == Running)
    assert(fsm.stateData == TargetRunning)
  }

  it("Should allow subscribing to callbacks for supervisor") {
    import FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    // val x = TargetInitialized
    val component = TestProbe()
    val fsm = newFSM(component.ref)

    val fakesupervisor = TestProbe()

    fsm ! SubscribeTransitionCallBack(fakesupervisor.ref)

    fakesupervisor.expectMsg(new CurrentState(fsm, Loaded))

    successfulInitialize(fsm, component)

    fakesupervisor.expectMsg(new Transition(fsm, Loaded, PendingInitializedFromLoaded))
    fakesupervisor.expectMsg(new Transition(fsm, PendingInitializedFromLoaded, Initialized))

    fsm ! UnsubscribeTransitionCallBack(fakesupervisor.ref)
  }

  it("Should allow subscribing to callbacks for test actor") {
    import FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    val component = TestProbe()
    val fsm = newFSM(component.ref)

    val transitionTester = system.actorOf(Props(new Actor {
      def receive = {
        case t @ Transition(_, PendingInitializedFromLoaded, Initialized) ⇒
          println(s"Transition PendingInitializedFromLoaded to Initialized successful: REGISTER WITH LOCATION")
        case Transition(_, Loaded, PendingInitializedFromLoaded) ⇒
          println("Reached PendingInitializedFromLoaded")
        case s @ CurrentState(_, _) ⇒
          assert(s === new CurrentState(fsm, Loaded))
      }
    }))

    fsm ! SubscribeTransitionCallBack(transitionTester)

    successfulInitialize(fsm, component)

    fsm ! UnsubscribeTransitionCallBack(transitionTester)
  }

  it("Should timeout after 5 seconds when no callback") {
    import FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    val component = TestProbe()
    val fsm = newFSM(component.ref)

    val stateProbe = TestProbe()

    fsm ! SubscribeTransitionCallBack(stateProbe.ref)

    stateProbe.expectMsg(new CurrentState(fsm, Loaded))

    // start the test
    fsm ! Initialize

    component.expectMsg(Initialize)

    assert(fsm.stateName === PendingInitializedFromLoaded)
    assert(fsm.stateData === TargetInitialized)

    import scala.concurrent.duration._

    // First get the transition to Pending
    stateProbe.expectMsg(new Transition(fsm, Loaded, PendingInitializedFromLoaded))

    // Now timeout for failure to respond
    stateProbe.expectMsg(5.seconds, new Transition(fsm, PendingInitializedFromLoaded, LifecycleFailure))

    component.expectMsg(LifecycleFailure(Initialized, "timeout out while waiting for component response"))

    fsm ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

  it("with lifecyclehandler, should accept Initialize") {
    import FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    val component = system.actorOf(Props(
      new Actor with Hcd with LifecycleHandler {
        def receive = lifecycleHandlerReceive
      }
    ), "LifecycleHandlerTester1")

    //val component = TestProbe()
    val fsm = newFSM(component)

    val stateProbe = TestProbe()

    fsm ! SubscribeTransitionCallBack(stateProbe.ref)

    stateProbe.expectMsg(new CurrentState(fsm, Loaded))

    fsm ! Startup

    stateProbe.expectMsg(new Transition(fsm, Loaded, PendingInitializedFromLoaded))
    stateProbe.expectMsg(new Transition(fsm, PendingInitializedFromLoaded, Initialized))
    // The following state is from Heartbeat
    stateProbe.expectMsg(new Transition(fsm, Initialized, Initialized))
    stateProbe.expectMsg(new Transition(fsm, Initialized, PendingRunningFromInitialized))
    stateProbe.expectMsg(new Transition(fsm, PendingRunningFromInitialized, Running))

    fsm ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

  it("with lifecyclehandler, startup Failure") {
    import FSM.{CurrentState, SubscribeTransitionCallBack, Transition}

    val failureReason = "Failing in Handler"

    val component = system.actorOf(Props(
      new Actor with Hcd with LifecycleHandler {
        def receive = lifecycleHandlerReceive

        override def startup(): HandlerResponse = {
          Failure(failureReason)
        }
      }
    ), "LifecycleHandlerTester2")

    //val component = TestProbe()
    val fsm = newFSM(component)

    val stateProbe = TestProbe()

    fsm ! SubscribeTransitionCallBack(stateProbe.ref)

    stateProbe.expectMsg(new CurrentState(fsm, Loaded))

    fsm ! Startup

    stateProbe.expectMsg(new Transition(fsm, Loaded, PendingInitializedFromLoaded))
    stateProbe.expectMsg(new Transition(fsm, PendingInitializedFromLoaded, Initialized))
    // The following state is from Heartbeat
    stateProbe.expectMsg(new Transition(fsm, Initialized, Initialized))
    stateProbe.expectMsg(new Transition(fsm, Initialized, PendingRunningFromInitialized))
    stateProbe.expectMsg(new Transition(fsm, PendingRunningFromInitialized, LifecycleFailure))

    // XXX Had some failed tests here, sometimes, but not always!
    println(s"\n\nXXX  assert(${fsm.stateName} === LifecycleFailure)\n\n")
    assert(fsm.stateName === LifecycleFailure)

    assert(fsm.stateData === FailureInfo(Running, failureReason))

    fsm ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

}
