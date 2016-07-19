package csw.examples.e2e.assembly

import akka.actor.{Actor, ActorLogging, ActorRef}
import csw.util.config.Events.EventTime
import csw.util.config.{DoubleItem, DoubleKey}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers}
import csw.util.config.ConfigDSL._

import TromboneAssembly._

/**
  * TMT Source Code: 6/21/16.
  */
class CalculationActor(tromboneControl: ActorRef) extends Actor with ActorLogging {

  import TromboneAssembly._

  var initialElevation: Double = 0.0

  def receive: Receive = {

    case UpdatedEventData(zenithAngle, focusError, time) =>
      assert(zenithAngle.units == degrees)
      val naLayerRange = naLayerRangeDistance(zenithAngle, focusError, time)
      val trombonePosition = calculateTrombonePosition(naLayerRange)
      tromboneControl ! HCDTrombonePosition(trombonePosition)


    case x => log.error(s"Unexpected message: $x")
  }

  // elevation(t+1) =  cos(theta(t))*(elevation(t)/cos(theta(t)) + g*focusRangeDistanceError(t))
  def calculateTrombonePosition(naLayerRangeDistance: DoubleItem): DoubleItem =
  set(trombonePositionKey, naLayerRangeDistance.head / 10).withUnits(micrometers)


  /**
    * Use the zenith angle, focus error, and event time to calculate the NA layer range distance.
    *
    * @param zenithAngle a zenith angle DoubleItme with units of kilometers
    * @param focusError
    * @param time
    * @return
    */
  def naLayerRangeDistance(zenithAngle: DoubleItem, focusError: DoubleItem, time: EventTime): DoubleItem = {
    set(naLayerRangeDistanceKey, zenithAngle.head + focusError.head).withUnits(kilometers)
  }

}