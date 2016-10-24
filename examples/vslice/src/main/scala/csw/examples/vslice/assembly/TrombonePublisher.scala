package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, Props}
import csw.examples.vslice.assembly.TrombonePublisher.{AOESWUpdate, AxisStateUpdate, AxisStatsUpdate, EngrUpdate}
import csw.services.events.{EventService, EventServiceSettings}
import csw.services.loc.LocationService.Location
import csw.services.loc.LocationSubscriberClient
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
 * @param assemblyContext the trombone AssemblyContext contains important shared values and useful functions
 * @param eventServiceIn optional EventService for testing event service
 */
class TrombonePublisher(assemblyContext: AssemblyContext, eventServiceIn: Option[EventService]) extends Actor with ActorLogging with TromboneStateHandler with LocationSubscriberClient {
  import assemblyContext._
  import TromboneStateActor._

  def receive: Receive = publishingEnabled(eventServiceIn)

  def publishingEnabled(eventService: Option[EventService]): Receive = stateReceive orElse {
    case AOESWUpdate(elevationItem, rangeItem) =>
      publishAOESW(eventService, elevationItem, rangeItem)

    case EngrUpdate(rtcFocusError, stagePosition, zenithAngle) =>
      publishEngr(eventService, rtcFocusError, stagePosition, zenithAngle)

    case ts: TromboneState =>
      publishState(eventService, ts)

    case AxisStateUpdate(axisName, position, state, inLowLimit, inHighLimit, inHome) =>
      publishAxisState(eventService, axisName, position, state, inLowLimit, inHighLimit, inHome)

    case AxisStatsUpdate(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount) =>
      publishAxisStats(eventService, axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case location: Location =>

    case x                  => log.error(s"Unexpected message in TrombonePublisher:publishingEnabled: $x")
  }

  def publishAOESW(eventService: Option[EventService], elevationItem: DoubleItem, rangeItem: DoubleItem) = {
    val se = SystemEvent(aoSystemEventPrefix).madd(elevationItem, rangeItem)
    log.info(s"System publish of $aoSystemEventPrefix: $se")
    eventService.foreach(_.publish(se))
  }

  def publishEngr(eventService: Option[EventService], rtcFocusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem) = {
    val ste = StatusEvent(engStatusEventPrefix).madd(rtcFocusError, stagePosition, zenithAngle)
    log.info(s"Status publish of $engStatusEventPrefix: $ste")
    eventService.foreach(_.publish(ste))
  }

  def publishState(eventService: Option[EventService], ts: TromboneState) = {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    val ste = StatusEvent(tromboneStateStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)
    log.debug(s"Status state publish of $tromboneStateStatusEventPrefix: $ste")
    eventService.foreach(_.publish(ste))
  }

  def publishAxisState(eventService: Option[EventService], axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem) = {
    val ste = StatusEvent(axisStateEventPrefix).madd(axisName, position, state, inLowLimit, inHighLimit, inHome)
    log.debug(s"Axis state publish of $axisStateEventPrefix: $ste")
    eventService.foreach(_.publish(ste))
  }

  def publishAxisStats(eventService: Option[EventService], axisName: StringItem, datumCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failureCount: IntItem, cancelCount: IntItem) = {
    val ste = StatusEvent(axisStatsEventPrefix).madd(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)
    log.debug(s"Axis stats publish of $axisStatsEventPrefix: $ste")
    eventService.foreach(_.publish(ste))
  }

}

object TrombonePublisher {
  def props(assemblyContext: AssemblyContext, eventService: Option[EventService] = None) = Props(classOf[TrombonePublisher], assemblyContext, eventService)

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