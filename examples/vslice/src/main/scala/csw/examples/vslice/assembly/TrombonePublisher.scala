package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import csw.examples.vslice.assembly.TrombonePublisher.{AOESWUpdate, AxisStateUpdate, AxisStatsUpdate, EngrUpdate}
import csw.services.events.{EventService, TelemetryService}
import csw.services.loc.LocationService.{Location, ResolvedTcpLocation, Unresolved}
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
 * The pubisher also publishes diagnostic data from the DiagPublisher as an axis state and statistics StatusEvent.
 *
 * Values in received messages are assumed to be correct and ready for publishing.
 *
 * @param assemblyContext the trombone AssemblyContext contains important shared values and useful functions
 * @param eventServiceIn optional EventService for testing event service
 * @param telemetryServiceIn optional Telemetryservice for testing with telemetry service
 */
class TrombonePublisher(assemblyContext: AssemblyContext, eventServiceIn: Option[EventService], telemetryServiceIn: Option[TelemetryService]) extends Actor with ActorLogging with TromboneStateClient with LocationSubscriberClient {
  import assemblyContext._
  import TromboneStateActor._
  implicit val system: ActorSystem = context.system

  // Needed for future onFailure
  import context.dispatcher

  log.info("Event Service in: " + eventServiceIn)
  log.info("Telemetry Service in: " + telemetryServiceIn)

  def receive: Receive = publishingEnabled(eventServiceIn, telemetryServiceIn)

  def publishingEnabled(eventService: Option[EventService], telemetryService: Option[TelemetryService]): Receive = {
    case AOESWUpdate(elevationItem, rangeItem) =>
      publishAOESW(eventService, elevationItem, rangeItem)

    case EngrUpdate(rtcFocusError, stagePosition, zenithAngle) =>
      publishEngr(telemetryService, rtcFocusError, stagePosition, zenithAngle)

    case ts: TromboneState =>
      publishState(telemetryService, ts)

    case AxisStateUpdate(axisName, position, state, inLowLimit, inHighLimit, inHome) =>
      publishAxisState(telemetryService, axisName, position, state, inLowLimit, inHighLimit, inHome)

    case AxisStatsUpdate(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount) =>
      publishAxisStats(telemetryService, axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case location: Location =>
      handleLocations(location, eventService, telemetryService)

    case x => log.error(s"Unexpected message in TrombonePublisher:publishingEnabled: $x")
  }

  def handleLocations(location: Location, currentEventService: Option[EventService], currentTelemetryService: Option[TelemetryService]): Unit = {
    location match {
      case t: ResolvedTcpLocation =>
        log.debug(s"Received TCP Location: ${t.connection}")
        // Verify that it is the event service
        if (t.connection == EventService.eventServiceConnection()) {
          val newEventService = Some(EventService.get(t.host, t.port))
          log.debug(s"Event Service at: $newEventService")
          context.become(publishingEnabled(newEventService, currentTelemetryService))
        }
        if (t.connection == TelemetryService.telemetryServiceConnection()) {
          val newTelemetryService = Some(TelemetryService.get(t.host, t.port))
          log.debug(s"Telemetry Service at: $newTelemetryService")
          context.become(publishingEnabled(currentEventService, newTelemetryService))
        }
      case u: Unresolved =>
        log.debug(s"Unresolved: ${u.connection}")
        if (u.connection == EventService.eventServiceConnection()) context.become(publishingEnabled(None, currentTelemetryService))
        else if (u.connection == TelemetryService.telemetryServiceConnection()) context.become(publishingEnabled(currentEventService, None))
      case default =>
        log.info(s"TrombonePublisher received some other location: $default")
    }
  }

  private def publishAOESW(eventService: Option[EventService], elevationItem: DoubleItem, rangeItem: DoubleItem) = {
    val se = SystemEvent(aoSystemEventPrefix).madd(elevationItem, rangeItem)
    log.debug(s"System publish of $aoSystemEventPrefix: $se")
    eventService.foreach(_.publish(se).onFailure {
      case ex => log.error("TrombonePublisher failed to publish AO system event: $se", ex)
    })
  }

  private def publishEngr(telemetryService: Option[TelemetryService], rtcFocusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem) = {
    val ste = StatusEvent(engStatusEventPrefix).madd(rtcFocusError, stagePosition, zenithAngle)
    log.info(s"Status publish of $engStatusEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onFailure {
      case ex => log.error(s"TrombonePublisher failed to publish engr event: $ste", ex)
    })
  }

  private def publishState(telemetryService: Option[TelemetryService], ts: TromboneState) = {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    val ste = StatusEvent(tromboneStateStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)
    log.info(s"Status state publish of $tromboneStateStatusEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onFailure {
      case ex => log.error(s"TrombonePublisher failed to publish trombone state: $ste", ex)
    })
  }

  private def publishAxisState(telemetryService: Option[TelemetryService], axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem) = {
    val ste = StatusEvent(axisStateEventPrefix).madd(axisName, position, state, inLowLimit, inHighLimit, inHome)
    log.debug(s"Axis state publish of $axisStateEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onFailure {
      case ex => log.error(s"TrombonePublisher failed to publish trombone axis state: $ste", ex)
    })
  }

  def publishAxisStats(telemetryService: Option[TelemetryService], axisName: StringItem, datumCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failureCount: IntItem, cancelCount: IntItem): Unit = {
    val ste = StatusEvent(axisStatsEventPrefix).madd(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)
    log.debug(s"Axis stats publish of $axisStatsEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onFailure {
      case ex => log.error(s"TrombonePublisher failed to publish trombone axis stats: $ste", ex)
    })
  }

}

object TrombonePublisher {
  def props(assemblyContext: AssemblyContext, eventService: Option[EventService] = None, telemetryService: Option[TelemetryService] = None) =
    Props(classOf[TrombonePublisher], assemblyContext, eventService, telemetryService)

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