package csw.examples.vslice.assembly

import akka.actor.ActorRef
import csw.examples.vslice.assembly.TromboneAssembly._
import csw.services.events
import csw.services.events.Subscriber
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config._
import csw.services.events.Implicits._

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneEventSubscriber(name: String, calculationActor: ActorRef) extends Subscriber[SystemEvent] {

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers
  var NSSinUse = false
  // This value is used when NSS is in Use
  val NSSzenithAngle: DoubleItem = zenithAngleKey.set(0.0f).withUnits(UnitsOfMeasure.degrees)
  // The following is updated when zenithAngle changes

  // Kim possibly set these initial values from config or get them from telemetry store
  var zenithAngle: DoubleItem = zenithAngleKey.set(0.0f).withUnits(UnitsOfMeasure.degrees)
  var focusError: DoubleItem = focusKey.set(0.0f).withUnits(UnitsOfMeasure.meters)

  def receive: Receive = {

    case event: SystemEvent =>
      event.info.source match {
        case `zConfigKey` =>
          // Update for zenith distance state then update calculator, however
          // only update calculator when NSS is not in use
          if (NSSinUse) {
            zenithAngle = NSSzenithAngle // This sets zenithANgle to 0 as per spec
          } else {
            zenithAngle = event(zenithAngleKey)
            updateCalculator(event.info.time)
          }

        case `focusConfigKey` =>
          // Update focusError state and then update calculator
          focusError = event(focusKey)
          updateCalculator(event.info.time)
      }

    case UsingNSS(inUse) =>
      NSSinUse = inUse
    case _ => println("some message")

  }

  /**
    * This function is called whenever a new event arrives. The function takes the current information consisting of
    * the zenithAngle and focusError which is actor state and forwards it to the CalculationActor
    *
    * @param eventTime - the time of the last event update
    */
  def updateCalculator(eventTime: EventTime) = {
    calculationActor ! UpdatedEventData(zenithAngle, focusError, eventTime)
  }

}



