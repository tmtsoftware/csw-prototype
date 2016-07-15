package csw.examples.e2e

import akka.actor.ActorRef
import csw.examples.e2e.CalculationActor.UpdatedEventData
import csw.services.event.EventSubscriber
import csw.util.config.Configurations.ConfigKey
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config.Subsystem.{RTC, TCS}
import csw.util.config.{FloatItem, FloatKey, UnitsOfMeasure}

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneEventSubscriber(name: String, calculationActor: ActorRef) extends EventSubscriber {

  import TromboneEventSubscriber._

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers
  var NSSinUse = false
  // This value is used when NSS is in Use
  val NSSzenithAngle: FloatItem = zenithAngleKey.set(0.0f).withUnits(UnitsOfMeasure.Deg)
  // The following is updated when zenithAngle changes

  // Kim possibly set these initial values from config or get them from telemetry store
  var zenithAngle: FloatItem = zenithAngleKey.set(0.0f).withUnits(UnitsOfMeasure.Deg)
  var focusError: FloatItem = focusKey.set(0.0f).withUnits(UnitsOfMeasure.Meters)

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

object TromboneEventSubscriber {

  // ConfigKeys for incoming event matching
  // This is the zenith angle from TCS
  val zConfigKey = ConfigKey(TCS, "tcsPk.zenithAngle")
  // This is the focus error from RTC
  val focusConfigKey = ConfigKey(RTC, "rtc.focusError")

  // Key values
  val focusKey = FloatKey("focus")
  val zenithAngleKey = FloatKey("zenithAngle")


  // Messages received by the TromboneSubscriber
  case class UsingNSS(inUse: Boolean)

}


