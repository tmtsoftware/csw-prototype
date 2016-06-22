import akka.actor.{Actor, ActorLogging}
import csw.util.config.Events.EventTime

/**
  * TMT Source Code: 6/21/16.
  */
class CalculationActor extends Actor with ActorLogging {

  def receive: Receive = {
    case x => log.error(s"Unexpected message: $x")
  }

}

object CalculationActor {

  // Messages received by CalculationActor
  // Update from subscribers
  case class UpdatedEventData(zenithDistance: Float, focusError: Float, time: EventTime)

}
