package csw.services.pkg

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import csw.services.loc.{Connection, ConnectionType}
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

  def newHcdSupervisor(): ActorRef = {
    import scala.concurrent.duration._

    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.SupervisorTests$SimpleTestHcd"

    val hcdInfo = HcdInfo(name, prefix, className, RegisterOnly, Set.empty, 1.second)

    val (componentActorSystem, actorRef) = Supervisor(hcdInfo)
    actorRef
  }

  val testAssemblyInfo: AssemblyInfo = {
    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.TestAssembly2"

    AssemblyInfo(name, prefix, className, RegisterOnly, Set.empty[ConnectionType], Set.empty[Connection])
  }

}

class SupervisorTests extends AkkaTestSpec {
  import SupervisorTests._
  import Supervisor._

  import scala.concurrent.duration._

  it("Should get one event for Initialize") {
    val stateProbe = TestProbe()
    val supervisor = newHcdSupervisor()
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)
    supervisor ! Initialize
    stateProbe.expectMsg(LifecycleStateChanged(Loaded))
    stateProbe.expectMsg(LifecycleStateChanged(Initialized))
    stateProbe.expectNoMsg(1.seconds)
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)
    system.stop(supervisor)
  }

  it("Should get two events for Startup") {
    val stateProbe = TestProbe()
    val supervisor = newHcdSupervisor()
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)
    supervisor ! Startup
    stateProbe.expectMsg(LifecycleStateChanged(Loaded))
    stateProbe.expectMsg(LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(LifecycleStateChanged(Running))
    stateProbe.expectNoMsg(1.seconds)
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)
    system.stop(supervisor)
  }

  it("Should get three events for Startup/Shutdown") {
    val stateProbe = TestProbe()
    val supervisor = newHcdSupervisor()
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)
    supervisor ! Startup
    stateProbe.expectMsg(LifecycleStateChanged(Loaded))
    stateProbe.expectMsg(LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(LifecycleStateChanged(Running))
    supervisor ! Shutdown
    stateProbe.expectMsg(LifecycleStateChanged(Initialized))
    stateProbe.expectNoMsg(1.seconds)
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)
    system.stop(supervisor)
  }

  it("Should get four events for Startup/Uninitialize") {
    val stateProbe = TestProbe()
    val supervisor = newHcdSupervisor()
    supervisor ! SubscribeLifecycleCallback(stateProbe.ref)
    supervisor ! Startup
    stateProbe.expectMsg(LifecycleStateChanged(Loaded))
    stateProbe.expectMsg(LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(LifecycleStateChanged(Running))
    supervisor ! Uninitialize
    stateProbe.expectMsg(LifecycleStateChanged(Initialized))
    stateProbe.expectMsg(LifecycleStateChanged(Loaded))
    stateProbe.expectNoMsg(1.seconds)
    supervisor ! UnsubscribeLifecycleCallback(stateProbe.ref)
    system.stop(supervisor)
  }
}

