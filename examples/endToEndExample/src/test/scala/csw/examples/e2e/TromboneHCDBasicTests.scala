package csw.examples.e2e

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, FunSpec, FunSpecLike, ShouldMatchers}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import csw.services.pkg.Component.HcdInfo

/**
  * TMT Source Code: 7/18/16.
  */
class TromboneHCDBasicTests extends TestKit(ActorSystem("TromboneHCDTests")) with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  import csw.util.config.ConfigDSL._
  import TromboneAssembly._

  val troboneAssemblyPrefix = "nfiraos.ncc.trombone"

  override def afterAll() {
    system.terminate()
  }

  describe("Testing of HCD") {

    it("Should receive setupconfig") {

      val hcdInfo = TromboneHCDApp.hcdInfo

      val th = TestActorRef(new TromboneHCD(hcdInfo))

      //th.underlyingActor.doProcess(defaultMoveSC)


    }

  }

}
