package csw.services.ccs

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import csw.services.ccs.CurrentStateReceiver.AddPublisher
import csw.util.akka.PublisherActor.Subscribe
import csw.util.config.Configurations.ConfigKey
import csw.util.config.StateVariable.CurrentState
import org.scalatest._

/**
 * TMT Source Code: 8/30/16.
 */
class CurrentStateReceiverTests extends TestKit(ActorSystem("TromboneAssemblyCommandHandlerTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  def stateReceiver = system.actorOf(CurrentStateReceiver.props)

  val ck1: String = "wfos.blue.filter"
  val ckw: ConfigKey = ck1
  val ck2: String = "tcs.tckPk.zenithAngle"
  val ckt: ConfigKey = ck2

  describe("Test basic operation") {

    it("should allow creation") {
      val sr = stateReceiver
      sr shouldNot be(null)
    }

    it("should allow adding 1 subscriber") {

      val currentStatePublisher = TestProbe()

      val handler = TestProbe()

      val sr = stateReceiver

      sr ! AddPublisher(currentStatePublisher.ref)

      handler.send(sr, Subscribe)

      currentStatePublisher.send(sr, CurrentState(ckw))

      val msg = handler.expectMsgClass(classOf[CurrentState])
      msg should be(CurrentState(ckw))
    }

    it("should allow adding multiple subscribers") {

      val currentStatePublisher = TestProbe()

      val handler1 = TestProbe()
      val handler2 = TestProbe()

      val sr = stateReceiver

      sr ! AddPublisher(currentStatePublisher.ref)

      handler1.send(sr, Subscribe)
      handler2.send(sr, Subscribe)

      currentStatePublisher.send(sr, CurrentState(ckw))

      val msg1 = handler1.expectMsgClass(classOf[CurrentState])
      msg1 should be(CurrentState(ckw))
      val msg2 = handler2.expectMsgClass(classOf[CurrentState])
      msg2 should be(CurrentState(ckw))
    }

    it("should allow multiple publishers 1 subscriber") {
      val currentStatePublisher1 = TestProbe()
      val currentStatePublisher2 = TestProbe()

      val handler1 = TestProbe()

      val sr = stateReceiver

      sr ! AddPublisher(currentStatePublisher1.ref)
      sr ! AddPublisher(currentStatePublisher2.ref)

      handler1.send(sr, Subscribe)

      currentStatePublisher1.send(sr, CurrentState(ckw))
      currentStatePublisher2.send(sr, CurrentState(ckt))

      handler1.expectMsg(CurrentState(ckw))
      handler1.expectMsg(CurrentState(ckt))
    }

    it("should allow multiple publishers multiple subscribers") {
      val currentStatePublisher1 = TestProbe()
      val currentStatePublisher2 = TestProbe()

      val handler1 = TestProbe()
      val handler2 = TestProbe()

      val sr = stateReceiver

      sr ! AddPublisher(currentStatePublisher1.ref)
      sr ! AddPublisher(currentStatePublisher2.ref)

      handler1.send(sr, Subscribe)
      handler2.send(sr, Subscribe)

      currentStatePublisher1.send(sr, CurrentState(ckw))
      currentStatePublisher2.send(sr, CurrentState(ckt))

      handler1.expectMsg(CurrentState(ckw))
      handler1.expectMsg(CurrentState(ckt))
      handler2.expectMsg(CurrentState(ckw))
      handler2.expectMsg(CurrentState(ckt))
    }
  }

}
