package csw.examples.vslice.assembly

import akka.actor.{ActorRef, Props}
import csw.examples.vslice.assembly.FollowActor.{UpdatedEventData, UsingNSS}
import csw.examples.vslice.assembly.TromboneAssembly._
import csw.services.events.{EventServiceSettings, EventSubscriber}
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config._

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneEventSubscriber(calculationActor: Option[ActorRef], eventServiceSettings: Option[EventServiceSettings]) extends EventSubscriber(eventServiceSettings) {

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers
  var NSSinUse = false
  // This value is used when NSS is in Use
  val NSSzenithAngle: DoubleItem = zenithAngleKey.set(0.0f).withUnits(focusErrorUnits)

  // Kim possibly set these initial values from config or get them from telemetry store
  // These vars are needed since updates from RTC and TCS will happen at different times and we need both values
  // Could have two events but that requries calculator to keep values
  var zenithAngle: DoubleItem = zenithAngleKey.set(0.0f).withUnits(zenithAngleUnits)
  var focusError: DoubleItem = focusErrorKey.set(0.0f).withUnits(focusErrorUnits)

  log.info(s"Subscribing to ${zConfigKey.prefix}, ${focusConfigKey.prefix}")
  subscribe(zConfigKey.prefix, focusConfigKey.prefix)

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
            log.info(s"Receved ZA: $event")
            updateCalculator(event.info.time)
          }

        case `focusConfigKey` =>
          // Update focusError state and then update calculator
          log.info(s"Receved FE: $event")
          focusError = event(focusErrorKey)
          updateCalculator(event.info.time)
      }

    case UsingNSS(inUse) => NSSinUse = inUse

    case x => log.error(s"TromboneEventSubscriber received an unknown message: $x")
  }

  /**
    * This function is called whenever a new event arrives. The function takes the current information consisting of
    * the zenithAngle and focusError which is actor state and forwards it to the CalculationActor
    *
    * @param eventTime - the time of the last event update
    */
  def updateCalculator(eventTime: EventTime) = {
    calculationActor.map(_ ! UpdatedEventData(zenithAngle, focusError, eventTime))
  }

}

object TromboneEventSubscriber {

  def props(actorRef: Option[ActorRef] = None, eventServiceSettings: Option[EventServiceSettings] = None) = Props(classOf[TromboneEventSubscriber], actorRef, eventServiceSettings)

}

