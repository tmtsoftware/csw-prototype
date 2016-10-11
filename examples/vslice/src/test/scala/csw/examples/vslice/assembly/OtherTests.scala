package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Inspectors, _}

/**
 * TMT Source Code: 8/28/16.
 */
class OtherTests extends TestKit(ActorSystem("OtherTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with Inspectors with BeforeAndAfterAll {

  import TromboneStateHandler._

  describe("Testing state item") {
    it("should allow creation") {
      val ts = TromboneState(cmdKey -> cmdUninitialized, moveKey -> moveUnindexed, sodiumKey -> false, nssKey -> false)

      ts.cmd.head should equal(cmdUninitialized)
      ts.move.head should equal(moveUnindexed)
      ts.sodiumLayer.head should equal(false)
      ts.nss.head should equal(false)
    }

    it("should allow changing just one") {
      var ts = TromboneState(cmdKey -> cmdUninitialized, moveKey -> moveUnindexed, sodiumKey -> false, nssKey -> false)

      ts = ts.copy(cmd = cmdItem(cmdReady))
      ts.cmd.head should equal(cmdReady)
      ts.move.head should equal(moveUnindexed)
      ts.sodiumLayer.head should equal(false)
      ts.nss.head should equal(false)
    }

  }

  class TestSubscriber(input: Int) extends Actor with ActorLogging with TromboneStateHandler {

    def receive: Receive = stateReceive orElse {
      case x => println(s"Got a bad message: $x")
    }
  }

  class TestPublisher(input: Int) extends Actor with ActorLogging with TromboneStateHandler {

    def receive: Receive = stateReceive orElse {
      case x => println(s"Got a bad message: $x")
    }
  }

  /**
   * Test Description: Ensure that publisher and subscribers are all updated properly
   */
  describe("Testing the trait approach") {
    it("should get updated") {
      // Test subscriber
      val ts: TestActorRef[TestSubscriber] = TestActorRef(Props(new TestSubscriber(0)), "TS")
      // Test publisher
      val tp: TestActorRef[TestPublisher] = TestActorRef(Props(new TestPublisher(0)), "TP")

      // Need this to check internal values
      val tsm = ts.underlyingActor
      val tpm = tp.underlyingActor

      // Default state
      tsm.cmd should be(cmdUninitialized)
      tsm.move should equal(moveUnindexed)
      tsm.sodiumLayer should equal(false)
      tsm.nss should equal(false)

      tpm.state(cmd = cmdReady, nss = true)

      // Check to make sure
      tsm.cmd should equal(cmdReady)
      tsm.move should equal(moveUnindexed)
      tsm.sodiumLayer should equal(false)
      tsm.nss should equal(true)

      tpm.state(move = moveIndexed)

      tsm.cmd should equal(cmdReady)
      tsm.move should equal(moveIndexed)
      tsm.sodiumLayer should equal(false)
      tsm.nss should equal(true)

      tpm.state(cmd = cmdBusy, sodiumLayer = true)

      tsm.cmd should equal(cmdBusy)
      tsm.move should equal(moveIndexed)
      tsm.sodiumLayer should equal(true)
      tsm.nss should equal(true)

      // Check updates the other way
      tsm.state(cmd = cmdContinuous, nss = false)

      tpm.cmd should equal(cmdContinuous)
      tpm.move should equal(moveIndexed)
      tpm.sodiumLayer should equal(true)
      tpm.nss should equal(false)

    }
  }

}
