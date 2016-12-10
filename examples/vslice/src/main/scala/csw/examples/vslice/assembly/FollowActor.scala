package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.AssemblyContext.{TromboneCalculationConfig, TromboneControlConfig}
import csw.examples.vslice.assembly.TromboneControl.GoToStagePosition
import csw.examples.vslice.assembly.TrombonePublisher.{AOESWUpdate, EngrUpdate}
import csw.util.config.{BooleanItem, DoubleItem}
import csw.util.config.Events.EventTime

/**
 * FollowActor uses events from TCS and RTC to calculate the position of the trombone assembly when in follow mode, which is set
 * using the follow command. While following, the follow actor calculates the position of the trombone axis and sends it to the
 * trombone HCD represented by the tromboneControl actor. The position is sent as a stage position in stage position units.
 *
 * FollowActor uses the ZenithAngle system event from the TCS and Focus Error system event from the RTC to make its
 * calculations. It receives this data in the form of UpdatedEventData messages from the TromboneEventSubscriber actor. This connection
 * is made in the FollowCommandActor. This is done to allow testing of the actors and functionality separately.
 *
 * FollowActor receives the calculation and control configurations and a flag BooleanItem called inNSSMode.  When inNSSMode is true,
 * the NFIRAOS Source Simulator is in use. In this mode, the FollowActor ignores the TCS zenith angle event data and provides 0.0 no
 * matter what the focus error.
 *
 * FollowActor also calculates the eng event and sodiumLayer telemetry events, which are sent while following. The sodiumLayer event
 * is only published when not in NSS mode according to my reading of the spec. All events are sent as messages to the TrombonePublisher
 * actor, which handles the connection to the event and telemetry services.  There is an aoPublisher and engPublisher in the constructor
 * of the actor to allow easier testing the publishing of the two types of events, but during operation both are set to the same
 * TrombonePublisher actor reference.
 *
 * @param ac AssemblyContext provides the configurations and other values
 * @param inNSSMode a BooleanItem set to true if the NFIRAOS Source Simulator is currently in use
 * @param tromboneControl an actorRef as [[scala.Option]] of the actor that writes the position to the trombone HCD
 * @param aoPublisher an actorRef as [[scala.Option]] of the actor that publishes the sodiumLayer event
 * @param engPublisher an actorRef as [[scala.Option]] of the actor that publishes the eng telemetry event
 */
class FollowActor(
    ac:                   AssemblyContext,
    val initialElevation: DoubleItem,
    val inNSSMode:        BooleanItem,
    val tromboneControl:  Option[ActorRef],
    val aoPublisher:      Option[ActorRef],
    val engPublisher:     Option[ActorRef]
) extends Actor with ActorLogging {

  import FollowActor._
  import Algorithms._
  import ac._

  val calculationConfig = ac.calculationConfig
  val controlConfig = ac.controlConfig

  // In this implementation, these vars are needed to support the setElevation and setAngle commands which require an update
  //val initialElevation: DoubleItem = initialElevationIn
  val initialFocusError: DoubleItem = focusErrorKey -> 0.0 withUnits focusErrorUnits
  val initialZenithAngle: DoubleItem = zenithAngleKey -> 0.0 withUnits zenithAngleUnits

  val nSSModeZenithAngle = zenithAngleKey -> 0.0 withUnits zenithAngleUnits

  // Initial receive - start with initial values
  def receive = followingReceive(initialElevation, initialFocusError, initialZenithAngle)

  def followingReceive(cElevation: DoubleItem, cFocusError: DoubleItem, cZenithAngle: DoubleItem): Receive = {

    case StopFollowing =>

    case UpdatedEventData(zenithAngleIn, focusErrorIn, time) =>
      log.info(s"Got an Update Event: $UpdatedEventData")
      // Not really using the time here
      // Units checks - should not happen, so if so, flag an error and skip calculation
      if (zenithAngleIn.units != zenithAngleUnits || focusErrorIn.units != focusErrorUnits) {
        log.error(s"Ignoring event data received with improper units: zenithAngle: ${zenithAngleIn.units}, focusError: ${focusErrorIn.units}")
      } else if (!verifyZenithAngle(zenithAngleIn) || !verifyFocusError(calculationConfig, focusErrorIn)) {
        log.error(s"Ignoring out of range event data: zenithAngle: $zenithAngleIn, focusError: $focusErrorIn")
      } else {
        // If inNSSMode is true, then we use angle 0.0
        // Do the calculation and send updates out
        val totalRangeDistance = focusZenithAngleToRangeDistance(calculationConfig, cElevation.head, focusErrorIn.head, zenithAngleIn.head)

        val newElevation = rangeDistanceToElevation(totalRangeDistance, zenithAngleIn.head)

        // Post a SystemEvent for AOESW if not inNSSMode according to spec
        if (!inNSSMode.head) {
          sendAOESWUpdate(naElevationKey -> newElevation withUnits naElevationUnits, naRangeDistanceKey -> totalRangeDistance withUnits naRangeDistanceUnits)
        }

        val newTrombonePosition = calculateNewTrombonePosition(calculationConfig, cElevation, focusErrorIn, zenithAngleIn)

        // Send the new trombone stage position to the HCD
        sendTrombonePosition(controlConfig, newTrombonePosition)

        // Post a StatusEvent for telemetry updates
        sendEngrUpdate(focusErrorIn, newTrombonePosition, zenithAngleIn)

        // Call again with new values - avoiding globals
        // I should be using newElevation, but it doesn't work well without changes in other values, so I'm not updating
        context.become(followingReceive(cElevation, focusErrorIn, zenithAngleIn))
      }

    case SetElevation(elevation) =>
      // This updates the current elevation and then causes an internal update to move things
      log.info(s"Got elevation: $elevation")
      // Restart the receive with the new value for elevation and the current values for others
      context.become(followingReceive(elevation, cFocusError, cZenithAngle))
      self ! UpdatedEventData(cZenithAngle, cFocusError, EventTime())

    case SetZenithAngle(zenithAngle) =>
      // This updates the current zenith angle and then causes an internal update to move things
      log.info(s"FollowActor setting angle to: $zenithAngle")
      // No need to call followReceive again since we are using the UpdateEventData message
      self ! UpdatedEventData(zenithAngle, cFocusError, EventTime())

    case x => log.error(s"Unexpected message in TromboneAssembly:FollowActor: $x")
  }

  def calculateNewTrombonePosition(calculationConfig: TromboneCalculationConfig, elevationIn: DoubleItem, focusErrorIn: DoubleItem, zenithAngleIn: DoubleItem): DoubleItem = {
    val totalRangeDistance = focusZenithAngleToRangeDistance(calculationConfig, elevationIn.head, focusErrorIn.head, zenithAngleIn.head)
    log.debug(s"totalRange: $totalRangeDistance")

    val stagePosition = rangeDistanceToStagePosition(totalRangeDistance)
    spos(stagePosition)
  }

  //
  def sendTrombonePosition(controlConfig: TromboneControlConfig, stagePosition: DoubleItem): Unit = {
    log.debug(s"Sending position: $stagePosition")
    tromboneControl.foreach(_ ! GoToStagePosition(stagePosition))
  }

  def sendAOESWUpdate(elevationItem: DoubleItem, rangeItem: DoubleItem): Unit = {
    log.debug(s"Publish aoUpdate: $aoPublisher $elevationItem, $rangeItem")
    aoPublisher.foreach(_ ! AOESWUpdate(elevationItem, rangeItem))
  }

  def sendEngrUpdate(focusError: DoubleItem, trombonePosition: DoubleItem, zenithAngle: DoubleItem): Unit = {
    log.debug(s"Publish engUpdate: " + engPublisher)
    engPublisher.foreach(_ ! EngrUpdate(focusError, trombonePosition, zenithAngle))
  }
}

object FollowActor {
  // Props for creating the follow actor
  def props(
    assemblyContext:  AssemblyContext,
    initialElevation: DoubleItem,
    inNSSModeIn:      BooleanItem,
    tromboneControl:  Option[ActorRef],
    aoPublisher:      Option[ActorRef] = None,
    engPublisher:     Option[ActorRef] = None
  ) = Props(classOf[FollowActor], assemblyContext, initialElevation, inNSSModeIn, tromboneControl, aoPublisher, engPublisher)

  /**
   * Messages received by csw.examples.vslice.FollowActor
   * Update from subscribers
   */
  trait FollowActorMessages

  case class UpdatedEventData(zenithAngle: DoubleItem, focusError: DoubleItem, time: EventTime) extends FollowActorMessages

  // Messages to Follow Actor
  case class SetElevation(elevation: DoubleItem) extends FollowActorMessages

  case class SetZenithAngle(zenithAngle: DoubleItem) extends FollowActorMessages

  case object StopFollowing extends FollowActorMessages

}