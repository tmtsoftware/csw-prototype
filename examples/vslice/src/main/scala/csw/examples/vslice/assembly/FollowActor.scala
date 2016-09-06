package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.util.config.DoubleItem
import csw.examples.vslice.assembly.FollowActor.CalculationConfig
import csw.util.config.Events.EventTime

/**
  * TMT Source Code: 6/21/16.
  */
class FollowActor(calculationConfig: CalculationConfig,
                  val tromboneControl: Option[ActorRef],
                  val aoPublisher: Option[ActorRef],
                  val engPublisher: Option[ActorRef]) extends Actor with ActorLogging {

  import TromboneAssembly._
  import FollowActor._
  import Algorithms._

  // Set to default so it's not necessary to always send a set initial message
  var initialElevation: DoubleItem = initialElevationKey -> calculationConfig.defaultInitialElevation withUnits initialElevationUnits

  var inNSSMode:Boolean = false
  val nSSModeZenithAngle = zenithAngleKey -> 0.0 withUnits zenithAngleUnits


  def receive: Receive = {

    case UsingNSS(inUse) => inNSSMode = inUse     // True if NSS is in use

    case UpdatedEventData(zenithAngleIn, focusError, time) =>
      // If inNSSMode is true, then we use angle 0.0
      val zenithAngle = if (inNSSMode) nSSModeZenithAngle else zenithAngleIn
      // Units checks - should not happen, so if so, flag an error and skip calculation
      // Not really using the time here
      if (zenithAngle.units != zenithAngleUnits || focusError.units != focusErrorUnits) {
        log.error(s"Ignoring event data received with improper units: zenithAngle: ${zenithAngle.units}, focusError: ${focusError.units}")
      } else if (!verifyZenithAngle(zenithAngle) || !verifyFocusError(focusError)) {
        log.error(s"Ignoring out of range event data: zenithAngle: $zenithAngle, focusError: $focusError")
      } else {
        // Do the calculation

        // This finds a new elevation based on the initial elevation estimate and the zenith angle
        val newElevation = naLayerElevation(calculationConfig, initialElevation.head, zenithAngle.head)
        log.debug(s"newElevation: $newElevation")

        // This calculates a new range distance based on the focus error
        val newRangeDistance = focusToRangeDistance(calculationConfig, focusError.head)
        log.debug(s"newRangeDistance: $newRangeDistance")

        // I just send the total of these two as the trombone state position to the trombone HCD after changing it to an encoder position
        val newTotalElevation = newElevation + newRangeDistance
        val newTrombonePosition:DoubleItem = stagePositionKey -> newTotalElevation withUnits stagePositionUnits

        // Send the new trombone stage position to the HCD
        sendTrombonePosition(newTrombonePosition)
        // Post a SystemEvent for AOESW
        sendAOESWUpdate(naLayerElevationKey -> newElevation withUnits naLayerElevationUnits,
                        naLayerRangeDistanceKey -> newRangeDistance withUnits naLayerRangeDistanceUnits)
        // Post a StatusEvent for telemetry updates
        sendEngrUpdate(focusError, newTrombonePosition, zenithAngle)
      }

    case SetElevation(elevation, zenithAngle) => initialElevation = elevation

    case x => log.error(s"Unexpected message in TromboneAssembly:CalculationActor: $x")
  }

  //
  def sendTrombonePosition(stagePosition: DoubleItem): Unit = {
    //log.info(s"Sending position: $trombonePosition")
    // Convert to encoder units
    // Need to get encoder position or send it
    tromboneControl.foreach(_ ! RangeDistance(stagePosition))
  }

  def sendAOESWUpdate(elevationItem: DoubleItem, rangeItem: DoubleItem): Unit = {
    aoPublisher.foreach(_ ! AOESWUpdate(elevationItem, rangeItem))
  }

  def sendEngrUpdate(focusError: DoubleItem, trombonePosition: DoubleItem, zenithAngle: DoubleItem): Unit = {
    engPublisher.foreach(_ ! EngrUpdate(focusError, trombonePosition, zenithAngle))
  }
}

object FollowActor {
  // Props for creating the calculation actor
  def props(calculationConfig: CalculationConfig, tromboneControl: Option[ActorRef] = None,
            aoPublisher: Option[ActorRef] = None,
            engPublisher: Option[ActorRef] = None) = Props(classOf[FollowActor], calculationConfig, tromboneControl, aoPublisher, engPublisher)

  /**
    * Configuration class
    *
    * @param defaultInitialElevation a default initial eleveation (possibly remove once workign)
    * @param focusErrorGain gain value for focus error
    * @param upperFocusLimit check for maximum focus error
    * @param lowerFocusLimit check for minimum focus error
    * @param zenithFactor an algorithm value for scaling zenith angle term
    */
  case class CalculationConfig(defaultInitialElevation: Double, focusErrorGain: Double,
                               upperFocusLimit: Double, lowerFocusLimit: Double, zenithFactor: Double)


  /**
    * Messages received by csw.examples.vslice.FollowActor
    * Update from subscribers
    */
  trait FollowActorMessages

  case class UpdatedEventData(zenithAngle: DoubleItem, focusError: DoubleItem, time: EventTime) extends FollowActorMessages

  // Messages to Calculation Actor
  case class SetElevation(elevation: DoubleItem, zenithAngle: DoubleItem) extends FollowActorMessages

  case class SetZenithAngle(zenithAngle: DoubleItem) extends FollowActorMessages

  // Messages received by the TromboneAssembly and TromboneSubscriber
  case class UsingNSS(inUse: Boolean) extends FollowActorMessages

}