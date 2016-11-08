package csw.services.ccs

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.ccs.CommandStatus2.{CommandStatus2, Completed, Error}
import csw.services.ccs.CurrentStateReceiver.{AddPublisher, RemovePublisher}
import csw.util.config.Configurations.ConfigKey
import csw.util.config.IntKey
import csw.util.config.UnitsOfMeasure.encoder
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, ShouldMatchers}
import csw.util.config.StateVariable.{CurrentState, DemandState}
import akka.pattern.{ask, pipe}
import csw.util.akka.PublisherActor.Subscribe

import scala.language.reflectiveCalls
import scala.concurrent.duration._

/**
 * TMT Source Code: 9/1/16.
 */
class StateMatcherTests extends TestKit(ActorSystem("TromboneAssemblyCommandHandlerTests")) with ImplicitSender
    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {
  override def afterAll: Unit = TestKit.shutdownActorSystem(system)

  // Needed for futures
  import system.dispatcher

  def stateReceiver: ActorRef = system.actorOf(CurrentStateReceiver.props)

  val movePrefix = s"WFOS.filter.move"
  val moveCK: ConfigKey = movePrefix
  val posKey = IntKey("position")
  def moveCS(pos: Int): CurrentState = CurrentState(moveCK).add(posKey -> pos withUnits encoder)

  val datumPrefix = s"WFOS.filter.datum"
  val datumCK: ConfigKey = datumPrefix
  // IF a dataum cs is found in the CurrentData then it's successful
  def datumCS() = CurrentState(datumCK)

  // Creates a list of CurrentStates for testing
  val listOfPosStates: List[CurrentState] = ((0 to 500 by 10).map(moveCS).toList :+ CurrentState(datumCK)) ++ (510 to 1000 by 10).map(moveCS).toList

  /**
   * Writes the CurrentState list to the CurrentStateReceiver in ActorRef. There can be a delay between each one
   * for more realism.
   * @param states a List of CurrentState objects
   * @param receiver an ActorRef to a created instance of CurrentStateReceiver
   * @param delay a time in milliseconds between writes of CurrentStatus
   */
  def writeStates(states: List[CurrentState], receiver: ActorRef, delay: Int = 5): Unit = {
    val fakePublisher = TestProbe()
    receiver ! AddPublisher(fakePublisher.ref)
    states.foreach { s =>
      receiver ! s
      Thread.sleep(delay)
    }
    receiver ! RemovePublisher(fakePublisher.ref)
  }

  // Creates a single matcher match actor
  def singleMatcher(currentStateReceiver: ActorRef, timeout: Timeout = Timeout(10.seconds)): ActorRef = {
    val props = SingleStateMatcherActor.props(currentStateReceiver, timeout)
    val stateMatcherActor = system.actorOf(props)
    stateMatcherActor
  }

  // Creates a multi matcher actor
  def multiMatcher(currentStateReceiver: ActorRef, timeout: Timeout = Timeout(10.seconds)): ActorRef = {
    val props = MultiStateMatcherActor.props(currentStateReceiver, timeout)
    val stateMatcherActor = system.actorOf(props)
    stateMatcherActor
  }

  /**
   * Test Description: These tests test the various matcher actors
   */
  describe("testing single item matcher") {
    // Needed for futures!
    implicit val timeout = Timeout(5.seconds)

    /**
     * Test Description: This is here to test that writeStates is working properly
     */
    it("should allow setup with fake current states") {
      val sr = stateReceiver

      val fakeMatcher = TestProbe()
      fakeMatcher.send(sr, Subscribe)

      writeStates(listOfPosStates, sr)

      val msgs = fakeMatcher.receiveN(listOfPosStates.size)
      msgs should equal(listOfPosStates)

      // Cleanup
      system.stop(sr)
    }

    /**
     * TestDescription: This test creates a single state matcher, feeds it a set of
     * fake CurrentStates and tests that it returns a Completed when it matches
     */
    it("single item match works") {
      import csw.services.ccs.SingleStateMatcherActor.StartMatch

      val sr = stateReceiver

      val fakeSender = TestProbe()

      val testPosition = 600
      val ds = DemandState(moveCK).add(posKey -> testPosition)
      val matcher = singleMatcher(sr)

      (matcher ? StartMatch(DemandMatcher(ds))).mapTo[CommandStatus2].pipeTo(fakeSender.ref)

      writeStates(listOfPosStates, sr)

      val msgOut = fakeSender.expectMsgClass(classOf[CommandStatus2])

      msgOut should be(Completed)

      // Cleanup
      system.stop(sr)
    }

    /**
     * TestDescription: This test creates a single state matcher, feeds it a set of
     * fake CurrentStates and tests that it returns an error when it times out.
     * This is the only way that a matcher looking at current state can fail at this point
     */
    it("single item times out with a failure when pos not found") {
      import csw.services.ccs.SingleStateMatcherActor.StartMatch
      val sr = stateReceiver

      val fakeSender = TestProbe()

      val testPosition = 901
      val ds = DemandState(moveCK).add(posKey -> testPosition)
      val matcher = singleMatcher(sr, 2.seconds)

      (matcher ? StartMatch(DemandMatcher(ds))).mapTo[CommandStatus2].pipeTo(fakeSender.ref)

      writeStates(listOfPosStates, sr)

      val msgOut = fakeSender.expectMsgClass(classOf[CommandStatus2])

      msgOut should be(Error("Current state matching timed out"))

      // Cleanup
      system.stop(sr)
    }

    /**
     * TestDescription: This test creates a multi state matcher, feeds it a set of
     * fake CurrentStates and tests that it returns a Completed when it matches a single current state
     */
    it("single item match works with multi matcher") {
      import csw.services.ccs.MultiStateMatcherActor.StartMatch

      val sr = stateReceiver

      val fakeSender = TestProbe()

      val testPosition = 200
      val ds = DemandState(moveCK).add(posKey -> testPosition)
      val matcher = multiMatcher(sr)

      (matcher ? StartMatch(DemandMatcher(ds))).mapTo[CommandStatus2].pipeTo(fakeSender.ref)

      writeStates(listOfPosStates, sr)

      val msgOut = fakeSender.expectMsgClass(classOf[CommandStatus2])

      msgOut should be(Completed)

      // Cleanup
      system.stop(sr)
    }

    /**
     * TestDescription: This test creates a multi state matcher, feeds it a set of
     * fake CurrentStates and tests that it returns a Completed when it matches multiple current states
     * in the same stream. This is looking for two matches with the same prefix!
     */
    it("multi item match works with multi matcher") {
      import csw.services.ccs.MultiStateMatcherActor.StartMatch

      val sr = stateReceiver

      val fakeSender = TestProbe()

      // Note that these are using the same prefix.
      val testPosition = 20
      val testPosition2 = 900
      val ds = DemandState(moveCK).add(posKey -> testPosition)
      val ds2 = DemandState(moveCK).add(posKey -> testPosition2)

      val matcher = multiMatcher(sr)

      (matcher ? StartMatch(DemandMatcher(ds2), DemandMatcher(ds))).mapTo[CommandStatus2].pipeTo(fakeSender.ref)

      writeStates(listOfPosStates, sr)

      val msgOut = fakeSender.expectMsgClass(classOf[CommandStatus2])

      msgOut should be(Completed)

      // Cleanup
      system.stop(sr)
    }

    /**
     * TestDescription: This test creates a multi state matcher, feeds it a set of
     * fake CurrentStates and tests that it returns a Completed when it matches multiple matchers
     * with different prefixes
     */
    it("multi item match works with multi matcher diffrent prefixes") {
      import csw.services.ccs.MultiStateMatcherActor.StartMatch

      val sr = stateReceiver

      val fakeSender = TestProbe()

      val testPosition = 750
      val ds = DemandState(moveCK).madd(posKey -> testPosition)

      val matcher = multiMatcher(sr)

      (matcher ? StartMatch(DemandMatcher(ds), PresenceMatcher(datumCK.prefix))).mapTo[CommandStatus2].pipeTo(fakeSender.ref)

      writeStates(listOfPosStates, sr)

      val msgOut = fakeSender.expectMsgClass(classOf[CommandStatus2])

      msgOut should be(Completed)

      // Cleanup
      system.stop(sr)
    }

    /**
     * TestDescription: This test creates a multi state matcher, feeds it a set of
     * fake CurrentStates and tests that it returns a Completed when a single matcher with
     * units enabled is used
     */
    it("multi item match works with multi matcher with units") {
      import csw.services.ccs.MultiStateMatcherActor.StartMatch

      val sr = stateReceiver

      val fakeSender = TestProbe()

      // Note that these are using the same prefix.
      val testPosition = 750

      val ds = DemandState(moveCK).madd(posKey -> testPosition withUnits encoder)

      val matcher = multiMatcher(sr)

      (matcher ? StartMatch(DemandMatcher(ds, withUnits = true))).mapTo[CommandStatus2].pipeTo(fakeSender.ref)

      writeStates(listOfPosStates, sr)

      val msgOut = fakeSender.expectMsgClass(classOf[CommandStatus2])

      msgOut should be(Completed)

      // Cleanup
      system.stop(sr)
    }
  }

}

