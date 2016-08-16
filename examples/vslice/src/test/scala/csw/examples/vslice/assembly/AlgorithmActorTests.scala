package csw.examples.vslice.assembly

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import csw.examples.vslice.assembly.CalculationActor.TromboneControlConfig
import csw.util.config.DoubleItem
import csw.util.config.Events.EventTime
import csw.util.config.UnitsOfMeasure.{degrees, encoder, kilometers, millimeters}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, ShouldMatchers}

import scala.concurrent.duration._

/**
  * TMT Source Code: 8/12/16.
  */
class AlgorithmActorTests extends TestKit(ActorSystem("TromboneAssemblyCalulationTests")) with ImplicitSender
  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {

  import TromboneAssembly._
  import AlgorithmData._

  override def afterAll = TestKit.shutdownActorSystem(system)

  val testControlConfig = TromboneControlConfig(
    defaultInitialElevation = 90.0, focusErrorGain = 1.0,
    upperFocusLimit = 20.0, lowerFocusLimit = -20.0,
    zenithFactor = 4.0, positionScale = 60.0,
    minElevation = 85.0, minElevationEncoder = 180)

  def ~=(x: Double, y: Double, precision: Double) = {
    if ((x - y).abs < precision) true else false
  }

  val initialElevation = 90.0

  def newCalculator(tromboneControl: ActorRef, publisher: ActorRef): TestActorRef[CalculationActor] = {
    val props = CalculationActor.props(testControlConfig, tromboneControl, publisher)
    TestActorRef(props)
  }


  describe("Basic tests for connectivity") {
    val fakeTC = TestProbe()
    val fakePub = TestProbe()

    it("shoudl allow creation with defaults") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      cal.underlyingActor.initialElevation should be(initialElevationKey -> testControlConfig.defaultInitialElevation withUnits (kilometers))

      fakeTC.expectNoMsg(1.seconds)
    }
  }

  describe("Test set initial elevation") {
    val fakeTC = TestProbe()
    val fakePub = TestProbe()

    it("should be default before") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      cal.underlyingActor.initialElevation should be(initialElevationKey -> testControlConfig.defaultInitialElevation withUnits (kilometers))
    }

    it("should change after setting") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      val newEl = initialElevationKey -> 85.0 withUnits (kilometers)

      cal ! SetElevation(newEl)

      cal.underlyingActor.initialElevation should be(newEl)
    }
  }

  def za(angle: Double): DoubleItem = zenithAngleKey -> angle withUnits (degrees)

  def fe(error: Double): DoubleItem = focusErrorKey -> error withUnits (millimeters)

  describe("Test for handling of Update events") {
    val fakeTC = TestProbe()
    val fakePub = TestProbe()

    it("should at least handle and send messages") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      // This should result in two messages being sent, one to each actor in the given order
      cal ! UpdatedEventData(za(0), fe(0), EventTime())

      fakeTC.expectMsgClass(classOf[HCDTrombonePosition])
      fakePub.expectMsgClass(classOf[AOESWUpdate])
    }
    it("should ignore if units wrong") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      // This should result in two messages being sent, one to each actor in the given order
      cal ! UpdatedEventData(zenithAngleKey -> 0, focusErrorKey -> 0, EventTime())

      fakeTC.expectNoMsg(100.milli)
    }
    it("should ignore if inputs out of range") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      // This should result in two messages being sent, one to each actor in the given order
      cal ! UpdatedEventData(za(-10), fe(0), EventTime())
      fakeTC.expectNoMsg(100.milli)

      cal ! UpdatedEventData(za(0.0), fe(22.0), EventTime())
      fakeTC.expectNoMsg(100.milli)
    }
  }

  describe("Test for reasonable results") {
    import AlgorithmData._
    val testFocus = 0.0
    val fakeTC = TestProbe()
    val fakePub = TestProbe()

    it("should work when only changing zenith angle") {
      val cal = newCalculator(fakeTC.ref, fakePub.ref)

      // Generate a list of fake event updates for a range of zenith angles and focus error 10mm
      val events = elevationTestValues.map(_._1).map(f => UpdatedEventData(za(f), fe(10.0), EventTime()))

      // Send the events to the calculation actor
      events.map(f => cal ! f)

      // Expect a set of AOESWUpdate messages to the fake publisher
      val aoEvts = fakePub.receiveN(elevationTestValues.size)
      info(s"aoEvts: $aoEvts")

      // Expect a set of HCDTrombonePosition messages to the fake trombone sender
      val trPos = fakeTC.receiveN(elevationTestValues.size)
      info(s"trPos: $trPos")

      // The following assumes we have models for what is to come out of the assembly.  Here we are just
      // reusing the actual equations to test that the events are proper
      val rangeExpected = CalculationActor.focusToRangeDistance(controlConfig, 10.0)
      val elExpected = elevationTestValues.map(_._1).map(f => CalculationActor.naLayerElevation(controlConfig, controlConfig.defaultInitialElevation, f)).map(f => AOESWUpdate(naLayerElevationKey -> f withUnits (kilometers), naLayerRangeDistanceKey -> rangeExpected withUnits (kilometers)))

      elExpected should equal(aoEvts)

      val posExpected = elExpected.map(f => (f.naElevation.head + f.naRange.head)).map(f => CalculationActor.rangeDistanceTransform(controlConfig, f)).map(f => HCDTrombonePosition(trombonePositionKey -> f withUnits (encoder)))
      posExpected should equal(trPos)

    }
  }
}
