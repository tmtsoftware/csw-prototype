package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.FollowActor.{SetZenithAngle, UpdatedEventData}
import csw.examples.vslice.assembly.TromboneAssembly.UpdateTromboneHCD
import csw.services.events.EventService
import csw.util.config.Events.EventTime
import csw.util.config.{BooleanItem, DoubleItem}

/**
 * FollowCommand encapsulates the actors that collaborate to implement the Follow command.
 *
 * A FollowCommand actor is created as a side effect when the trombone assembly receives the Follow command. This actor is created
 * and exists after the Follow command and until the Stop command is received.
 *
 * Follow command creates a TromboneControl actor which receives stage position updates.
 * It creates a TromboneEventSubscriber, which subscribes to the zenith angle system events from the TCS, and the focus error system
 * events from the RTC. The TromboneEventSubscriber forwards these updates to the follow actor, which processes them and computes new
 * trombone stage positions as well as other events.
 *
 * The publisher of events is injected into the FollowCommand and injected into the FollowActor. This is done because the publisher is
 * shared in the Assembly by other actors.
 *
 * The follow command acts differently depending on the value of nssInUseIn, which is true of the NFIRAOS Source Simulator is in use or not.
 * See specs, but when in use, the Follow Actor ignores zenith angle updates. When nssInUse is true, the TromboneEventSubscriber always sends
 * the value of 0.0 for the zenith angle no matter what.
 *
 * nssInUse can be updated while operational. This is only used for testing to ensure that the zero angle behavior is working (see tests).
 * During operation, a new FollowCommand is created when the use of the NSS changes. If following, a Stop must be issued, and a new Follow
 * command with nssInUse set accordingly.
 *
 * Note that the actor receive method is parameterized with an optional HCD actor ref. The HCD actor is set initially when
 * the actor is created and may be updated if the actor goes down or up. The actor ref is an [[scala.Option]] so
 * that if the actor ref is set to None, no message will be sent, but the actor can operator normally. FollowCommand forwards this update
 * to the TromboneControl
 *
 * When the FollowCommand actor is killed, all its created actors are killed. The subscriptions to the event service and telemetry service
 * are removed.
 *
 * While "following" the trombone assembly can accept the SetAngle and SetElevation commands, which are forwarded to the Follow actor
 * that executes the algorithms for the trombone assembly.
 *
 * @param ac the trombone Assembly context contains shared values and functions
 * @param nssInUseIn a BooleanItem, set to true if the NFIRAOS Source Simulator is in use, set to false if not in use
 * @param tromboneHCDIn the actor reference to the trombone HCD as a [[scala.Option]]
 * @param eventPublisher the actor reference to the shared TrombonePublisher actor as a [[scala.Option]]
 * @param eventService EventService for subscriptions
 *
 */
class FollowCommand(ac: AssemblyContext, initialElevation: DoubleItem, val nssInUseIn: BooleanItem, val tromboneHCDIn: Option[ActorRef], eventPublisher: Option[ActorRef], eventService: EventService) extends Actor with ActorLogging {
  import FollowCommand._

  // Create the trombone publisher for publishing SystemEvents to AOESW, etc if one is not provided
  val tromboneControl = context.actorOf(TromboneControl.props(ac, tromboneHCDIn), "trombonecontrol")
  // These vals are only being created to simplify typing
  val initialFollowActor = createFollower(initialElevation, nssInUseIn, tromboneControl, eventPublisher, eventPublisher)
  val initialEventSubscriber = createEventSubscriber(nssInUseIn, initialFollowActor, eventService)

  def receive: Receive = followReceive(nssInUseIn, initialFollowActor, initialEventSubscriber, tromboneHCDIn)

  def followReceive(nssInUse: BooleanItem, followActor: ActorRef, eventSubscriber: ActorRef, tromboneHCD: Option[ActorRef]): Receive = {
    case StopFollowing =>
      log.debug("Receive stop following in Follow Command")
      // Send this so that unsubscriptions happen, need to check if needed
      context.stop(eventSubscriber)
      context.stop(followActor)
      context.stop(self)

    case UpdateNssInUse(nssInUseUpdate) =>
      if (nssInUseUpdate != nssInUse) {
        // First stop the currents so we can create new ones
        context.stop(eventSubscriber)
        context.stop(followActor)
        // Note that follower has the option of a different publisher for events and telemetry, but this is primarily useful for testing
        val newFollowActor = createFollower(initialElevation, nssInUseUpdate, tromboneControl, eventPublisher, eventPublisher)
        val newEventSubscriber = createEventSubscriber(nssInUseUpdate, newFollowActor, eventService)
        // Set a new receive method with updated actor values, prefer this over vars or globals
        context.become(followReceive(nssInUseUpdate, newFollowActor, newEventSubscriber, tromboneHCD))
      }

    case m @ SetZenithAngle(zenithAngle) =>
      log.debug(s"Got angle: $zenithAngle")
      followActor.forward(m)

    // Note that this is an option so it can be None
    case upd: UpdateTromboneHCD =>
      // Set a new receive method with updated actor values and new HCD, prefer this over vars or globals
      context.become(followReceive(nssInUse, followActor, eventSubscriber, upd.tromboneHCD))
      // Also update the trombone control with the new HCD reference
      tromboneControl ! UpdateTromboneHCD(upd.tromboneHCD)

    case UpdateZAandFE(zenithAngleIn, focusErrorIn) =>
      followActor ! UpdatedEventData(zenithAngleIn, focusErrorIn, EventTime())

    case x => log.error(s"Unexpected message received in TromboneAssembly:FollowCommand: $x")
  }

  private def createFollower(initialElevation: DoubleItem, nssInUse: BooleanItem, tromboneControl: ActorRef, eventPublisher: Option[ActorRef], telemetryPublisher: Option[ActorRef]): ActorRef =
    context.actorOf(FollowActor.props(ac, initialElevation, nssInUse, Some(tromboneControl), eventPublisher, eventPublisher), "follower")

  private def createEventSubscriber(nssItem: BooleanItem, followActor: ActorRef, eventService: EventService): ActorRef =
    context.actorOf(TromboneEventSubscriber.props(ac, nssItem, Some(followActor), eventService), "eventsubscriber")

}

object FollowCommand {

  def props(assemblyContext: AssemblyContext, initialElevation: DoubleItem, nssInUse: BooleanItem, tromboneHCD: Option[ActorRef], eventPublisherIn: Option[ActorRef], eventService: EventService) =
    Props(classOf[FollowCommand], assemblyContext, initialElevation, nssInUse, tromboneHCD, eventPublisherIn, eventService)

  trait FollowCommandMessages

  case object StopFollowing extends FollowCommandMessages

  case class UpdateNssInUse(nssInUse: BooleanItem) extends FollowCommandMessages

  /**
   * This is an engineering and test method that is used to trigger the same kind of update as a zenith angle and focus error
   * events from external to the Assembly
   * @param zenithAngle a zenith angle in degrees as a [[csw.util.config.DoubleItem]]
   * @param focusError a focus error value in
   */
  case class UpdateZAandFE(zenithAngle: DoubleItem, focusError: DoubleItem)

}
