package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, Props}
import csw.examples.vslice.assembly.TrombonePublisher.{AOESWUpdate, AxisStateUpdate, AxisStatsUpdate, EngrUpdate}
import csw.services.events.{EventService, EventServiceSettings}
import csw.util.config._
import csw.util.config.Events.{StatusEvent, SystemEvent}

/**
 * An actor that provides the publishing interface to the TMT Event Service and Telemetry Service.
 *
 * The TrombonePublisher receives messages from other actors that need to publish an event of some kind. The messages are
 * repackaged as SystemEvents or StatusEvents as needed.
 *
 * Currently, this actor publishes the sodiumLayer System Event for RTC, and the engr StatusEvent
 * and the state StatusEvent. The sodiumLayer event is triggered by the arrival of an AOESWUpdate message. The engr
 * StatusEvent is triggered by the arrival of an EngrUpdate message, and the state StatusEvent is triggered by the
 * TromboneState message.
 *
 * Values in messages are assumed to be correct and ready for publishing.
 *
 * @param ac the trombone AssemblyContext contains important shared values and useful functions
 * @param settings optional paramters for connecting to a testing event service
 */
class TrombonePublisher(ac: AssemblyContext, settings: Option[EventServiceSettings]) extends Actor with ActorLogging with TromboneStateHandler {
  import TromboneStateHandler._
  import ac._

  val eventService = EventService(settings.getOrElse(EventServiceSettings(context.system)))

  def receive: Receive = publishingEnabled

  def publishingEnabled: Receive = stateReceive orElse {
    case AOESWUpdate(elevationItem, rangeItem)                 => publishAOESW(elevationItem, rangeItem)
    case EngrUpdate(rtcFocusError, stagePosition, zenithAngle) => publishEngr(rtcFocusError, stagePosition, zenithAngle)
    case ts: TromboneState                                     => publishState(ts)
    case AxisStateUpdate(axisName, position, state, inLowLimit, inHighLimit, inHome) =>
      publishAxisState(axisName, position, state, inLowLimit, inHighLimit, inHome)
    case AxisStatsUpdate(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount) =>
      publishAxisStats(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case x => log.error(s"Unexpected message in TrombonePublisher:publishingEnabled: $x")
  }

  def publishAOESW(elevationItem: DoubleItem, rangeItem: DoubleItem) = {
    val se = SystemEvent(aoSystemEventPrefix).madd(elevationItem, rangeItem)
    log.info(s"System publish of $aoSystemEventPrefix: $se")
    eventService.publish(se)
  }

  def publishEngr(rtcFocusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem) = {
    val ste = StatusEvent(engStatusEventPrefix).madd(rtcFocusError, stagePosition, zenithAngle)
    log.info(s"Status publish of $engStatusEventPrefix: $ste")
    eventService.publish(ste)
  }

  def publishState(ts: TromboneState) = {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    val ste = StatusEvent(tromboneStateStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)
    log.debug(s"Status state publish of $tromboneStateStatusEventPrefix: $ste")
    eventService.publish(ste)
  }

  def publishAxisState(axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem) = {
    val ste = StatusEvent(axisStateEventPrefix).madd(axisName, position, state, inLowLimit, inHighLimit, inHome)
    log.debug(s"Axis state publish of $axisStateEventPrefix: $ste")
    eventService.publish(ste)
  }

  def publishAxisStats(axisName: StringItem, datumCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failureCount: IntItem, cancelCount: IntItem) = {
    val ste = StatusEvent(axisStatsEventPrefix).madd(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)
    log.debug(s"Axis stats publish of $axisStatsEventPrefix: $ste")
    eventService.publish(ste)
  }

}

object TrombonePublisher {
  def props(assemblyContext: AssemblyContext, settings: Option[EventServiceSettings] = None) = Props(classOf[TrombonePublisher], assemblyContext, settings)

  /**
   * Used by actors wishing to cause an event for AO ESW
   * @param naElevation elevation update
   * @param naRange range update
   */
  case class AOESWUpdate(naElevation: DoubleItem, naRange: DoubleItem)

  /**
   * Used by actors wishing to cause an engineering event update
   * @param focusError focus error value as DoubleItem
   * @param stagePosition stage position as a DoubleItem
   * @param zenithAngle zenith angle update as a DoubleItem
   */
  case class EngrUpdate(focusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem)

  case class AxisStateUpdate(axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem)

  case class AxisStatsUpdate(axisName: StringItem, initCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failCount: IntItem, cancelCount: IntItem)

}