package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, Props}
import csw.services.events.{EventService, EventServiceSettings}
import csw.util.config.Events.{StatusEvent, SystemEvent}

/**
  * TMT Source Code: 8/16/16.
  */
class TrombonePublisher(settings: Option[EventServiceSettings]) extends Actor with ActorLogging with TromboneStateHandler {
  import TromboneAssembly._
  import TromboneStateHandler._

  val eventService = EventService(settings.getOrElse(EventServiceSettings(context.system)))

  def receive: Receive = {
    case AOESWUpdate(elevationItem, rangeItem) =>
      val se = SystemEvent(aoSystemEventPrefix).madd(elevationItem, rangeItem)
      log.info(s"Publish: $se")
      eventService.publish(se)
    case EngrUpdate(rtcFocusError, stagePosition, zenithAngle) =>
      val te = StatusEvent(telStatusEventPrefix).madd(rtcFocusError, stagePosition, zenithAngle)
      log.info(s"Status publish: $te")
      eventService.publish(te)
    case ts:TromboneState =>
      // We can do this for convenience rather than using TromboneStateHandler's stateReceive
      val te = StatusEvent(telStatusEventPrefix).madd(ts.cmd, ts.move, ts.sodiumLayer, ts.nss)
      log.info(s"Status publish: $ts")
      eventService.publish(te)

    case x => log.error(s"Unexpected message in TrombonePublisher:receive: $x")
  }

}

object TrombonePublisher {
  def props(settings: Option[EventServiceSettings] = None) = Props(classOf[TrombonePublisher], settings)
}