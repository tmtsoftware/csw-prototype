package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.util.config.Events.EventTime
import csw.util.config.{DoubleItem, DoubleKey}
import csw.util.config.UnitsOfMeasure._
import csw.util.config.ConfigDSL._
import TromboneAssembly._
import csw.examples.vslice.assembly.CalculationActor.TromboneControlConfig

/**
  * TMT Source Code: 6/21/16.
  */
class CalculationActor(controlConfig: TromboneControlConfig, tromboneControl: ActorRef, publisher: ActorRef) extends Actor with ActorLogging {

  import TromboneAssembly._
  import CalculationActor._

  // Set to default so it's not necessary to always send a set initial message
  var initialElevation: DoubleItem = initialElevationKey -> controlConfig.defaultInitialElevation withUnits (kilometers)

  def receive: Receive = {

    case UpdatedEventData(zenithAngle, focusError, time) =>
      // Units checks - should not happen, so if so, flag an error and skip calculation
      // Not really using the time here
      if (zenithAngle.units != degrees || focusError.units != millimeters) {
        log.error(s"Ignoring event data received with improper units: zenithAngle: ${zenithAngle.units}, focusError: ${focusError.units}")
      } else if (!verifyZenithAngle(zenithAngle) || !verifyFocusError(focusError)) {
        log.error(s"Ignoring out of range event data: zenithAngle: $zenithAngle, focusError: $focusError")
      } else {
        // Do the calculation
        val newElevation = naLayerElevation(controlConfig, initialElevation.head, zenithAngle.head)
        log.debug(s"newElevation: $newElevation")
        val newRangeDistance = focusToRangeDistance(controlConfig, focusError.head)
        log.debug(s"newRangeDistance: $newRangeDistance")
        // Send the trombone position to the tromboneControl
        val newTrombonePosition = rangeDistanceTransform(controlConfig, newElevation + newRangeDistance)
        sendTrombonePosition(trombonePositionKey -> newTrombonePosition withUnits (encoder))
        sendAOESWUpdate(naLayerElevationKey -> newElevation withUnits (kilometers), naLayerRangeDistanceKey -> newRangeDistance withUnits (kilometers))
      }
    case SetElevation(elevation) =>
      initialElevation = elevation
    case x =>
      log.error(s"Unexpected message in TromboneAssembly:CalculationActor: $x")
  }

  //
  def sendTrombonePosition(trombonePosition: DoubleItem): Unit = {
    tromboneControl ! HCDTrombonePosition(trombonePosition)
  }

  def sendAOESWUpdate(elevationItem: DoubleItem, rangeItem: DoubleItem): Unit = {
    publisher ! AOESWUpdate(elevationItem, rangeItem)
  }
}

object CalculationActor {
  // Props for creating the calculation actor
  def props(controlConfig: TromboneControlConfig, tromboneControl: ActorRef, publisher: ActorRef) = Props(classOf[CalculationActor], controlConfig, tromboneControl, publisher)

  /**
    * Configuration class
    *
    * @param defaultInitialElevation
    * @param focusErrorGain
    * @param upperFocusLimit
    * @param lowerFocusLimit
    * @param zenithFactor
    * @param positionScale
    * @param minElevation
    * @param minElevationEncoder
    */
  case class TromboneControlConfig(defaultInitialElevation: Double, focusErrorGain: Double,
                                   upperFocusLimit: Double, lowerFocusLimit: Double,
                                   zenithFactor: Double, positionScale: Double, minElevation: Double, minElevationEncoder: Int)

  /**
    * Arbitrary check of the zenith angle to be within bounds
    *
    * @param zenithAngle DoubleItem that contains zenith angle
    * @return true if valid else false
    */
  def verifyZenithAngle(zenithAngle: DoubleItem): Boolean = zenithAngle.head < 90.0 && zenithAngle.head >= 0.0

  /**
    * Checking the input focus error against fake limits of +/- 20
    *
    * @param focusError DoubleItem that contains focusError
    * @return true if valid else false
    */
  def verifyFocusError(focusError: DoubleItem): Boolean = focusError.head >= -20.0 && focusError.head <= 20.0

  // From Dan K's email,
  // elevation(t+1) =  cos(theta(t))*(elevation(t)/cos(theta(t)) + g*focusRangeDistanceError(t))
  // For this example, layer elevation is the initial elevation + a factor * cosine of zenith angle designed to be 90 to 100 km
  def naLayerElevation(controlConfig: TromboneControlConfig, initialElevation: Double, zenithAngle: Double): Double =
  Math.cos(Math.toRadians(zenithAngle)) * controlConfig.zenithFactor + initialElevation

  // The focus to range distance is  the gain * focus value/4 where focus is pinned to be between +/-
  def focusToRangeDistance(controlConfig: TromboneControlConfig, focusError: Double): Double = {
    // Limit the focus Error
    val pinnedFocusValue = Math.max(controlConfig.lowerFocusLimit, Math.min(controlConfig.upperFocusLimit, focusError))
    controlConfig.focusErrorGain * pinnedFocusValue / 4.0
  }

  /**
    * NALayerRange = zfactor*zenithAngle + focusError
    *
    * zfactor is read from configuration
    *
    * @param rangeElTotal is the calculated value of the range distance + NA layer elevation
    * @return DoubleItem with key naTrombonePosition and units of enc
    */
  def rangeDistanceTransform(controlConfig: TromboneControlConfig, rangeElTotal: Double): Int = {
    // Scale value to be between 200 and 1000 encoder
    (controlConfig.positionScale * (rangeElTotal - controlConfig.minElevation) + controlConfig.minElevationEncoder).toInt
  }

}