package csw.examples.e2e.assembly

import akka.actor.{Actor, ActorLogging}
import csw.examples.e2e.hcd.TromboneHCD

/**
  * TMT Source Code: 7/15/16.
  */
class TromboneControl(actorRef: TromboneHCD) extends Actor with ActorLogging {

  import TromboneAssembly._

  def receive: Receive = {
    case HCDTrombonePosition(position) =>
      log.info(s"Setting trombone current to: ${position.head} with units: ${position.units}")
      assert(position.head > 0 && position.head < 90)
    // Sendcommand to HCD here
    case x => log.error(s"Unexpected message: $x")
  }

}
