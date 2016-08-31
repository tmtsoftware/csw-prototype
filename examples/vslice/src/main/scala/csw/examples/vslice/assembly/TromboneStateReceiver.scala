package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef}
import csw.util.akka.PublisherActor.Subscribe
import csw.util.config.StateVariable.CurrentState

/**
  * TMT Source Code: 8/26/16.
  */
class TromboneStateReceiver(hcdPublisher: Option[ActorRef]) extends Actor with ActorLogging {

  // Subscribe to the stream of CurrentStatus events
  hcdPublisher.foreach(_ ! Subscribe)


  def receive: Receive = {

    case x@CurrentState =>
      log.info(s"Current state - should be published for telemetry: $x")

    case x => log.error("TromboneStateReceiver received an unknown message: $x")
  }
}
