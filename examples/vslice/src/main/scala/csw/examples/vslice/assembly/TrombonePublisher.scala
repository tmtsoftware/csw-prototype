package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, Props}
import csw.examples.vslice.assembly.TrombonePublisher.{AOESWUpdate, AxisStateUpdate, AxisStatsUpdate, EngrUpdate}
import csw.services.events.{EventService, TelemetryService}
import csw.services.loc.LocationService.{Location, ResolvedTcpLocation, Unresolved}
import csw.services.loc.LocationSubscriberClient
import csw.util.config._
import csw.util.config.Events.{StatusEvent, SystemEvent}

import scala.concurrent.Future

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
 * @param telemetryServiceIn optional Telemetryservice for testing with telemetry service
 */
class TrombonePublisher(assemblyContext: AssemblyContext, eventServiceIn: Option[EventService], telemetryServiceIn: Option[TelemetryService]) extends Actor with ActorLogging with TromboneStateClient with LocationSubscriberClient {
  import assemblyContext._
  import TromboneStateActor._

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
      log.info("Got Axis State Update!")
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
        log.info(s"Received TCP Location: ${t.connection}")
        // Verify that it is the event service
        if (t.connection == EventService.eventServiceConnection()) {
          log.info(s"TrombonePublisher received connection: $t")
          val newEventService = Some(EventService.get(t.host, t.port))
          log.info(s"Event Service at: $newEventService")
          context.become(publishingEnabled(newEventService, currentTelemetryService))
        }
        if (t.connection == TelemetryService.telemetryServiceConnection()) {
          log.info(s"TrombonePublisher received connection: $t")
          val newTelemetryService = Some(TelemetryService.get(t.host, t.port))
          log.info(s"Telemetry Service at: $newTelemetryService")
          context.become(publishingEnabled(currentEventService, newTelemetryService))
        }
      case u: Unresolved =>
        log.info(s"Unresolved: ${u.connection}")
        if (u.connection == EventService.eventServiceConnection()) context.become(publishingEnabled(None, currentTelemetryService))
        if (u.connection == TelemetryService.telemetryServiceConnection()) context.become(publishingEnabled(currentEventService, None))
      case default =>
        log.info(s"TrombonePublisher received some other location: $default")
    }
  }

  def publishAOESW(eventService: Option[EventService], elevationItem: DoubleItem, rangeItem: DoubleItem) = {
    val se = SystemEvent(aoSystemEventPrefix).madd(elevationItem, rangeItem)
    log.info(s"System publish of $aoSystemEventPrefix: $se")
    eventService.foreach(_.publish(se))
  }

  def publishEngr(telemetryService: Option[TelemetryService], rtcFocusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem) = {
    val ste = StatusEvent(engStatusEventPrefix).madd(rtcFocusError, stagePosition, zenithAngle)
    log.info(s"Status publish of $engStatusEventPrefix: $ste")
    telemetryService match {
      case Some(es) =>
        val f = es.publish(ste)
        f.onFailure {
          case t => log.error(s"TrombonePublisher failed to publish engr: $ste")
        }
      case None =>
        log.info("TrombonePublisher has no Telemetry Service")
    }
  }

  def publishState(telemetryService: Option[TelemetryService], ts: TromboneState) = {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    val ste = StatusEvent(tromboneStateStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)
    log.debug(s"Status state publish of $tromboneStateStatusEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste))
  }

  def publishAxisState(telemetryService: Option[TelemetryService], axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem) = {
    import context.dispatcher
    val ste = StatusEvent(axisStateEventPrefix).madd(axisName, position, state, inLowLimit, inHighLimit, inHome)
    log.info(s"Axis state publish of $axisStateEventPrefix: $ste")
    val f: Future[Unit] = telemetryService.get.publish(ste)
    f.onFailure {
      case t => log.info(s"Failed to publish axis state: $t")
    }
  }

  def publishAxisStats(telemetryService: Option[TelemetryService], axisName: StringItem, datumCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failureCount: IntItem, cancelCount: IntItem) = {
    val ste = StatusEvent(axisStatsEventPrefix).madd(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)
    log.debug(s"Axis stats publish of $axisStatsEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste))
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