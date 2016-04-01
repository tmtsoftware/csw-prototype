package csw.services.pkg

import akka.actor.FSM.UnsubscribeTransitionCallBack
import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.services.pkg.Component._
import csw.services.pkg.LifecycleManager._
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, MustMatchers}

abstract class AkkaTestSpec extends TestKit(ActorSystem()) with ImplicitSender
    with FunSpecLike with MustMatchers with BeforeAndAfterAll {
  override def afterAll = TestKit.shutdownActorSystem(system)
}

object SupervisorTests {

  case class SimpleTestHcd(hcdInfo: HcdInfo) extends Hcd with LifecycleHandler {
    def receive = lifecycleHandlerReceive
  }

  case class SimpleTestAssembly(assemblyInfo: AssemblyInfo) extends Assembly with LifecycleHandler {
    def receive = lifecycleHandlerReceive
  }
}

class SupervisorTests extends AkkaTestSpec {
  import Supervisor._

  import scala.concurrent.duration._

  def nilActor = TestProbe().ref

  def newTestHcd() = {
    val component = system.actorOf(Props(
      new Actor with Hcd with LifecycleHandler {
        def receive = lifecycleHandlerReceive
      }), "LifecycleHandlerTester1")
    component
  }

  def newTestAssembly() = {
    val component = system.actorOf(Props(
      new Actor with Assembly with LifecycleHandler {
        def receive = lifecycleHandlerReceive
      }), "LifecycleHandlerTester1")
    component
  }

  def newHcdSupervisor(): ActorRef = {
    import scala.concurrent.duration._

    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.SupervisorTests$SimpleTestHcd"

    val hcdInfo = HcdInfo(name, prefix, className, RegisterOnly, Set.empty, 1.second)

    val actorRef = Supervisor(hcdInfo)
    actorRef
  }

  def testAssemblyInfo: AssemblyInfo = {
    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.TestAssembly2"
    // val className = "csw.services.pkg.SupervisorTests$SimpleTestAssembly"

    AssemblyInfo(name, prefix, className, RegisterOnly, Set.empty[ConnectionType], Set.empty[Connection])
  }

  def newAssemblySupervisor: ActorRef = Supervisor(testAssemblyInfo)

  it("Should get one event for Initialize") {
    val supervisor = newHcdSupervisor()

    val stateProbe = TestProbe()

    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    supervisor ! Initialize

    stateProbe.expectMsg(new LifecycleStateChanged(Initialized))
    stateProbe.expectNoMsg(1.seconds)

    supervisor ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

  it("Should get two events for Startup") {
    val supervisor = newHcdSupervisor()

    val stateProbe = TestProbe()

    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    supervisor ! Startup

    stateProbe.expectMsg(new LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(new LifecycleStateChanged(Running))
    stateProbe.expectNoMsg(1.seconds)

    supervisor ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

  it("Should get three events for Startup/Shutdown") {
    val supervisor = newHcdSupervisor()

    val stateProbe = TestProbe()

    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    supervisor ! Startup

    stateProbe.expectMsg(new LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(new LifecycleStateChanged(Running))

    supervisor ! Shutdown

    stateProbe.expectMsg(new LifecycleStateChanged(Initialized))
    stateProbe.expectNoMsg(1.seconds)

    supervisor ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

  it("Should get four events for Startup/Uninitialize") {
    val supervisor = newHcdSupervisor()

    val stateProbe = TestProbe()

    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)

    supervisor ! Startup

    stateProbe.expectMsg(new LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(new LifecycleStateChanged(Running))

    supervisor ! Uninitialize

    stateProbe.expectMsg(new LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(new LifecycleStateChanged(Loaded))

    stateProbe.expectNoMsg(1.seconds)

    supervisor ! UnsubscribeTransitionCallBack(stateProbe.ref)
  }

  it("Should create an Assembly") {

    val probe = TestProbe()

    val actorRef = TestActorRef(new Supervisor(testAssemblyInfo))

    //actorRef ! Startup

    // println(actorRef.underlyingActor.component)

    //println(s"Assembly: ${actorRef.underlyingActor.componentInfo}")

    probe.expectNoMsg(25.seconds)

  }
}
/*
case class TestAssembly2(info: AssemblyInfo) extends Assembly with AssemblyController with LifecycleHandler with TimeServiceScheduler {
  import Supervisor._
  import TimeService._

  object Tick
  object End
  object Close

  log.info(s"Freq: ${context.system.scheduler.maxFrequency}")

  log.info("Startup called")
  lifecycle(supervisor, Startup)

  val killer = scheduleOnce(localTimeNow.plusSeconds(10), self, End)

  var count = 0

  def componentReceive: Receive = {
    case End ⇒
      // Need to unregister with the location service (Otherwise application won't exit)
      //posEventGenerator ! End
      haltComponent(supervisor)
  }

  def receive = componentReceive orElse lifecycleHandlerReceive orElse controllerReceive

}


*/
