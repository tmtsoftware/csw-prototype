//package csw.examples.vsliceJava.assembly
//
//import akka.actor.{Actor, ActorLogging, ActorRef, Props}
//import csw.util.config.DoubleItem
//
///**
//  * TMT Source Code: 6/21/16.
//  */
//class CalculationActor(calculationConfig: CalculationConfig,
//                       val tromboneControl: Option[ActorRef],
//                       val aoPublisher: Option[ActorRef],
//                       val engPublisher: Option[ActorRef]) extends Actor with ActorLogging {
//
//  import CalculationActor._
//  import TromboneAssembly._
//
//  // Set to default so it's not necessary to always send a set initial message
//  var initialElevation: DoubleItem = initialElevationKey -> calculationConfig.defaultInitialElevation withUnits initialElevationUnits
//
//  var inNSSMode:Boolean = false
//  val nSSModeZenithAngle = zenithAngleKey -> 0.0 withUnits zenithAngleUnits
//
//
//  def receive: Receive = {
//
//    case UsingNSS(inUse) => inNSSMode = inUse     // True if NSS is in use
//
//    case UpdatedEventData(zenithAngleIn, focusError, time) =>
//      // If inNSSMode is true, then we use angle 0.0
//      val zenithAngle = if (inNSSMode) nSSModeZenithAngle else zenithAngleIn
//      // Units checks - should not happen, so if so, flag an error and skip calculation
//      // Not really using the time here
//      if (zenithAngle.units != zenithAngleUnits || focusError.units != focusErrorUnits) {
//        log.error(s"Ignoring event data received with improper units: zenithAngle: ${zenithAngle.units}, focusError: ${focusError.units}")
//      } else if (!verifyZenithAngle(zenithAngle) || !verifyFocusError(focusError)) {
//        log.error(s"Ignoring out of range event data: zenithAngle: $zenithAngle, focusError: $focusError")
//      } else {
//        // Do the calculation
//        val newElevation = naLayerElevation(calculationConfig, initialElevation.head, zenithAngle.head)
//        log.debug(s"newElevation: $newElevation")
//        val newRangeDistance = focusToRangeDistance(calculationConfig, focusError.head)
//        log.debug(s"newRangeDistance: $newRangeDistance")
//        // Send the trombone position to the tromboneControl
//        val newTotalElevation = newElevation + newRangeDistance
//        val newTrombonePosition:DoubleItem = stagePositionKey -> newTotalElevation withUnits stagePositionUnits
//        // Send the new trombone stage position to the HCD
//        sendTrombonePosition(newTrombonePosition)
//        // Post a SystemEvent for AOESW
//        sendAOESWUpdate(naLayerElevationKey -> newElevation withUnits naLayerElevationUnits,
//                        naLayerRangeDistanceKey -> newRangeDistance withUnits naLayerRangeDistanceUnits)
//        // Post a StatusEvent for telemetry updates
//        sendEngrUpdate(focusError, newTrombonePosition, zenithAngle)
//      }
//
//    case SetElevation(elevation) => initialElevation = elevation
//
//    case x => log.error(s"Unexpected message in TromboneAssembly:CalculationActor: $x")
//  }
//
//  //
//  def sendTrombonePosition(trombonePosition: DoubleItem): Unit = {
//    //log.info(s"Sending position: $trombonePosition")
//    tromboneControl.foreach(_ ! HCDTromboneUpdate(trombonePosition))
//  }
//
//  def sendAOESWUpdate(elevationItem: DoubleItem, rangeItem: DoubleItem): Unit = {
//    aoPublisher.foreach(_ ! AOESWUpdate(elevationItem, rangeItem))
//  }
//
//  def sendEngrUpdate(focusError: DoubleItem, trombonePosition: DoubleItem, zenithAngle: DoubleItem): Unit = {
//    engPublisher.foreach(_ ! EngrUpdate(focusError, trombonePosition, zenithAngle))
//  }
//}
//
//object CalculationActor {
//  // Props for creating the calculation actor
//  def props(calculationConfig: CalculationConfig, tromboneControl: Option[ActorRef] = None,
//            aoPublisher: Option[ActorRef] = None,
//            engPublisher: Option[ActorRef] = None) = Props(classOf[CalculationActor], calculationConfig, tromboneControl, aoPublisher, engPublisher)
//
//  /**
//    * Configuration class
//    *
//    * @param defaultInitialElevation a default initial eleveation (possibly remove once workign)
//    * @param focusErrorGain gain value for focus error
//    * @param upperFocusLimit check for maximum focus error
//    * @param lowerFocusLimit check for minimum focus error
//    * @param zenithFactor an algorithm value for scaling zenith angle term
//    */
//  case class CalculationConfig(defaultInitialElevation: Double, focusErrorGain: Double,
//                               upperFocusLimit: Double, lowerFocusLimit: Double, zenithFactor: Double)
//
//  /**
//    * Arbitrary check of the zenith angle to be within bounds
//    *
//    * @param zenithAngle DoubleItem that contains zenith angle
//    * @return true if valid else false
//    */
//  def verifyZenithAngle(zenithAngle: DoubleItem): Boolean = zenithAngle.head < 90.0 && zenithAngle.head >= 0.0
//
//  /**
//    * Checking the input focus error against fake limits of +/- 20
//    *
//    * @param focusError DoubleItem that contains focusError
//    * @return true if valid else false
//    */
//  def verifyFocusError(focusError: DoubleItem): Boolean = focusError.head >= -20.0 && focusError.head <= 20.0
//
//  // From Dan K's email,
//  // elevation(t+1) =  cos(theta(t))*(elevation(t)/cos(theta(t)) + g*focusRangeDistanceError(t))
//  // For this example, layer elevation is the initial elevation + a factor * cosine of zenith angle designed to be 90 to 100 km
//  def naLayerElevation(calculationConfig: CalculationConfig, initialElevation: Double, zenithAngle: Double): Double =
//                       Math.cos(Math.toRadians(zenithAngle)) * calculationConfig.zenithFactor + initialElevation
//
//  // The focus to range distance is  the gain * focus value/4 where focus is pinned to be between +/- 20
//  def focusToRangeDistance(calculationConfig: CalculationConfig, focusError: Double): Double = {
//    // Limit the focus Error
//    val pinnedFocusValue = Math.max(calculationConfig.lowerFocusLimit, Math.min(calculationConfig.upperFocusLimit, focusError))
//    calculationConfig.focusErrorGain * pinnedFocusValue / 4.0
//  }
//
//
//
//}