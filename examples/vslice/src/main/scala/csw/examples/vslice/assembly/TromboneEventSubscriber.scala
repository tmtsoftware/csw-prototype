package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.FollowActor.{StopFollowing, UpdatedEventData}
import csw.examples.vslice.assembly.TromboneEventSubscriber.UpdateNssInUse
import csw.services.events.EventService
import csw.services.loc.LocationService.{Location, ResolvedTcpLocation}
import csw.util.config.Configurations.ConfigKey
import csw.util.config.Events.{EventTime, SystemEvent}
import csw.util.config._

/**
 * TMT Source Code: 6/20/16.
 */
class TromboneEventSubscriber(ac: AssemblyContext, nssInUseIn: BooleanItem, followActor: Option[ActorRef], eventService: Option[EventService]) extends Actor with ActorLogging {

  import ac._

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers

  // This value is used when NSS is in Use
  final val nssZenithAngle: DoubleItem = za(0.0)

  // Kim possibly set these initial values from config or get them from telemetry store
  // These vars are needed since updates from RTC and TCS will happen at different times and we need both values
  // Could have two events but that requries follow actor to keep values
  val initialZenithAngle: DoubleItem = if (nssInUseIn.head) nssZenithAngle else za(0.0)
  val initialFocusError: DoubleItem = fe(0.0)
  // This is used to keep track since it can be updated
  var nssInUseGlobal = nssInUseIn

  def connectingReceive: Receive = {
    case location: Location => location match {
      case t: ResolvedTcpLocation =>
        // Verify that it is the event service
        if (t.connection == EventService.eventServiceConnection) {
          log.info("Subscriber received connection")
          val eventService: EventService = EventService.get(t.host, t.port)
          log.info("Event Service at: " + eventService)
          startupSubscriptions(eventService)
          subscribeReceive(eventService, nssInUseIn, initialZenithAngle, initialFocusError)
        }
      case _ =>
        log.info(s"EventSubscriber received location: $location")
    }
  }

  private def startupSubscriptions(eventService: EventService): Unit = {
    // Allways subscribe to focus error
    subscribeKeys(eventService, feConfigKey)

    log.info("nssInuse: " + nssInUseIn)

    // But only subscribe to ZA if nss is not in use
    if (!nssInUseIn.head) {
      // NSS not inuse so subscribe to ZA
      subscribeKeys(eventService, zaConfigKey)
    }
  }

  //  def receive: Receive = subscribeReceive(nssInUseIn, initialZenithAngle, initialFocusError)
  def receive: Receive = connectingReceive

  def subscribeReceive(eventService: EventService, cNssInUse: BooleanItem, cZenithAngle: DoubleItem, cFocusError: DoubleItem): Receive = {

    case event: SystemEvent =>
      event.info.source match {
        case `zaConfigKey` =>
          val newZenithAngle = event(zenithAngleKey)
          log.info(s"Received ZA: $event")
          updateFollowActor(newZenithAngle, cFocusError, event.info.time)
          // Pass the new values to the next message
          context.become(subscribeReceive(eventService, cNssInUse, newZenithAngle, cFocusError))

        case `feConfigKey` =>
          // Update focusError state and then update calculator
          log.info(s"Received FE: $event")
          val newFocusError = event(focusErrorKey)
          updateFollowActor(cZenithAngle, newFocusError, event.info.time)
          // Pass the new values to the next message
          context.become(subscribeReceive(eventService, cNssInUse, cZenithAngle, newFocusError))
      }

    case StopFollowing =>
      // Kill this subscriber
      context.stop(self)

    // This is an engineering command to allow checking subscriber
    case UpdateNssInUse(nssInUseUpdate) =>
      if (nssInUseUpdate != cNssInUse) {
        if (nssInUseUpdate.head) {
          unsubscribeKeys(eventService, zaConfigKey)
          context.become(subscribeReceive(eventService, nssInUseUpdate, nssZenithAngle, cFocusError))
        } else {
          subscribeKeys(eventService, zaConfigKey)
          context.become(subscribeReceive(eventService, nssInUseUpdate, cZenithAngle, cFocusError))
        }
        // Need to update the global for shutting down event subscriptions
        nssInUseGlobal = nssInUseUpdate
      }

    case x => log.error(s"Unexpected message received in TromboneEventSubscriber:subscribeReceive: $x")
  }

  def unsubscribeKeys(eventService: EventService, configKeys: ConfigKey*): Unit = {
    log.info(s"Unsubscribing to: $configKeys")
    //eventService.unsubscribe(configKeys.map(_.prefix): _*)
  }

  def subscribeKeys(eventService: EventService, configKeys: ConfigKey*): Unit = {
    log.info(s"Subscribing to: $configKeys")
    eventService.subscribe(Some(self), None, configKeys.map(_.prefix): _*)
  }

  /*
  override def postStop(): Unit = {
    if (!nssInUseGlobal.head) {
      unsubscribeKeys(zaConfigKey)
    }
    unsubscribeKeys(feConfigKey)
  }
  */

  /**
   * This function is called whenever a new event arrives. The function takes the current information consisting of
   * the zenithAngle and focusError which is actor state and forwards it to the FoolowActor if present
   *
   * @param eventTime - the time of the last event update
   */
  def updateFollowActor(zenithAngle: DoubleItem, focusError: DoubleItem, eventTime: EventTime) = {
    followActor.foreach(_ ! UpdatedEventData(zenithAngle, focusError, eventTime))
  }

}

object TromboneEventSubscriber {

  /**
   * props for the TromboneEventSubscriber
   * @param followActor a FollowActor as an Option[ActorRef]
   * @param eventService for testing, an event Service can be provided
   * @return Props for TromboneEventSubscriber
   */
  def props(assemblyContext: AssemblyContext, nssInUse: BooleanItem, followActor: Option[ActorRef] = None, eventService: Option[EventService] = None) =
    Props(classOf[TromboneEventSubscriber], assemblyContext, nssInUse, followActor, eventService)

  case class UpdateNssInUse(nssInUse: BooleanItem)
}

