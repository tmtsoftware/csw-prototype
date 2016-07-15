package csw.examples.e2e

import akka.actor.{Actor, ActorLogging}
import csw.examples.e2e.CalculationActor.UpdatedEventData
import csw.util.config.Events.EventTime
import csw.util.config.FloatItem
import csw.util.config.UnitsOfMeasure.Deg

/**
  * TMT Source Code: 6/21/16.
  */
class CalculationActor extends Actor with ActorLogging {

  var currentElevation: Double = 0.0
  var nextElevation: Double = 0.0

  def receive: Receive = {
    case UpdatedEventData(zenithAngle, focusError, time) =>
      assert(zenithAngle.units == Deg)
      val naLayerRange = NALayerRangeDistance(zenithAngle, focusError, time)


    case x => log.error(s"Unexpected message: $x")
  }

  // elevation(t+1) =  cos(theta(t))*(elevation(t)/cos(theta(t)) + g*focusRangeDistanceError(t))
  def rangeDistanceTransform(calcOutput: Double): Double = calcOutput / 10

  def NALayerRangeDistance(zenithAngle: FloatItem, focusError: FloatItem, time: EventTime): Float = {
    zenithAngle.head + focusError.head
  }

}

object CalculationActor {

  // Messages received by csw.examples.e2e.CalculationActor
  // Update from subscribers
  case class UpdatedEventData(zenithAngle: FloatItem, focusError: FloatItem, time: EventTime)

}
