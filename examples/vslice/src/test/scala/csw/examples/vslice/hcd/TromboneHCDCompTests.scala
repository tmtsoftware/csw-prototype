package csw.examples.vslice.hcd

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.Supervisor
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, _}
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

/**
  * TMT Source Code: 7/27/16.
  */
class TromboneHCDCompTests extends TestKit(ActorSystem("TromboneTests")) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  override def afterAll = TestKit.shutdownActorSystem(system)

  val testInfo = HcdInfo(TromboneHCD.componentName,
    TromboneHCD.trombonePrefix,
    TromboneHCD.componentClassName,
    DoNotRegister, Set(AkkaType), 1.second)

  val troboneAssemblyPrefix = "nfiraos.ncc.trombone"

  def startHCD: ActorRef = {
    val testInfo = HcdInfo(TromboneHCD.componentName,
      TromboneHCD.trombonePrefix,
      TromboneHCD.componentClassName,
      DoNotRegister, Set(AkkaType), 1.second)
    Supervisor(testInfo)
  }

  describe("create a tls") {
    val hcd = startHCD

    it("should not be null") {
      hcd should not be null
    }

    Thread.sleep(3)
  }


  def stopComponent(supervisorSystem: ActorSystem, supervisor: ActorRef, timeout: FiniteDuration) = {
    //system.scheduler.scheduleOnce(timeout) {
    println("STOPPING")
    Supervisor.haltComponent(supervisor)
    Await.ready(supervisorSystem.whenTerminated, 5.seconds)
    system.terminate()
    System.exit(0)
    //}
  }

}
