package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging}
import csw.examples.vslice.assembly.TromboneAssembly.AOESWUpdate
import csw.services.events.{EventService, EventServiceSettings}
import csw.util.config.Events.SystemEvent

/**
  * TMT Source Code: 8/16/16.
  */
class TrombonePublisher extends Actor with ActorLogging {

  import scala.concurrent.duration._
  import TromboneAssembly._

  val settings = EventServiceSettings(context.system)
  val eventService = EventService(settings)

  def receive: Receive = {
    case AOESWUpdate(elevationItem, rangeItem) =>
      val se = SystemEvent(aoSystemEventPrefix).madd(elevationItem, rangeItem)
      eventService.set(se)

    case x => log.error(s"Unexpected message in TrombonePublisher:receive: $x")
  }

}

object TrombonePublisher {

}