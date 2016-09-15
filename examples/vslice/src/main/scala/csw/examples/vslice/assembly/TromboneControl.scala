package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.TromboneControl.TromboneControlConfig
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.HcdController
import csw.services.ccs.HcdController.Submit
import csw.util.config.Configurations.SetupConfig
import csw.util.config.DoubleItem
import csw.util.config.UnitsOfMeasure.millimeters

/**
 * TMT Source Code: 7/15/16.
 */
class TromboneControl(controlConfig: TromboneControlConfig, tromboneHCD: Option[ActorRef]) extends Actor with ActorLogging {
  import TromboneControl._
  import TromboneAssembly._
  import TromboneHCD._

  def receive: Receive = {
    case RangeDistance(newPosition) =>

      // Convert to encoder units
      val encoderPosition = rangeDistanceTransform(controlConfig, newPosition)
      // First convert the
      assert(encoderPosition > 0 && encoderPosition < 1500)

      log.info(s"Setting trombone current to: ${encoderPosition}")

      // Send command to HCD here
      tromboneHCD.foreach(_ ! Submit(positionSC(encoderPosition)))

    case RawPosition(newPosition) =>
      val pinnedFocusValue = Math.max(controlConfig.minEncoderLimit, Math.min(controlConfig.maxEncoderLimit, newPosition.head))
      tromboneHCD.foreach(_ ! Submit(SetupConfig(axisMoveCK).add(positionKey -> pinnedFocusValue)))

      log.info(s"Setting raw trombone position to ${pinnedFocusValue}")

    case x => log.error(s"Unexpected message: $x")
  }

}

object TromboneControl {
  // Props for creating the TromboneControl actor
  def props(controlConfig: TromboneControlConfig, tromboneHCD: Option[ActorRef]) = Props(classOf[TromboneControl], controlConfig, tromboneHCD)

  /**
   * Configuration class
   *
   * @param positionScale
   * @param minElevation
   * @param minEncoderLimit
   */
  case class TromboneControlConfig(positionScale: Double, minElevation: Double, minElevationEncoder: Int, minEncoderLimit: Int, maxEncoderLimit: Int)

  /**
   * NALayerRange = zfactor*zenithAngle + focusError
   *
   * zfactor is read from configuration
   *
   * @param newPosition is the value of the stage position in millimeters (currently the total NA elevation)
   * @return DoubleItem with key naTrombonePosition and units of enc
   */
  def rangeDistanceTransform(controlConfig: TromboneControlConfig, newPosition: DoubleItem): Int = {
    assert(newPosition.units == millimeters)
    // Scale value to be between 200 and 1000 encoder
    (controlConfig.positionScale * (newPosition.head - controlConfig.minElevation) + controlConfig.minElevationEncoder).toInt
  }

}
