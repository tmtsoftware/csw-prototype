package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import csw.examples.vslice.assembly.TromboneAssembly.UpdateTromboneHCD
import csw.examples.vslice.assembly.TrombonePublisher.{AxisStateUpdate, AxisStatsUpdate}
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.HcdController
import csw.services.loc.LocationService._
import csw.services.loc.LocationSubscriberClient
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.akka.PublisherActor
import csw.util.config.StateVariable.CurrentState

/**
 * DiagPublisher provides diagnostic telemetry in the form of two events. DiagPublisher operaties in the 'OperationsState' or 'DiagnosticState'.
 *
 * DiagPublisher listens in on axis state updates from the HCD and publishes them as a StatusEvent through the assembly's event publisher.
 * In OperationsState, it publishes every 5'th axis state update (set with val operationsSkipCount.
 * In DiagnosticState, it publishes every other axis update (more frequent in diagnostic state).
 *
 * context.become is used to implement a state machine with two states operationsReceive and diagnosticReceive
 *
 * In DiagnosticState, it also publishes an axis statistics event every second. Every one second (diagnosticAxisStatsPeriod), it
 * sends the GetAxisStats message to the HCD. When the data arrives, it is sent to the event publisher.
 *
 * This actor demonstrates a few techniques. First, it has no variables. Each state in the actor is represented by its own
 * receive method. Each method has parameters that can be called from within the function with updated values eliminating the need
 * for variables.
 *
 * This shows how to filter events from the CurrentState stream from the HCD.
 *
 * This shows how to use the TimeService to send periodic messages and how to periodically call another actor and process its
 * response.
 *
 * @param assemblyContext      the assembly context provides overall assembly information and convenience functions
 * @param tromboneHCDIn        initial actorRef of the tromboneHCD as a [[scala.Option]]
 * @param eventPublisher     initial actorRef of an instance of the TrombonePublisher as [[scala.Option]]
 */
class DiagPublisher(assemblyContext: AssemblyContext, tromboneHCDIn: Option[ActorRef], eventPublisher: Option[ActorRef]) extends Actor with ActorLogging with TimeServiceScheduler with LocationSubscriberClient {

  import DiagPublisher._
  import TromboneHCD._
  import csw.services.ts.TimeService._

  // Subscribe to CurrentState if there is an input HCD
  tromboneHCDIn.foreach(_ ! PublisherActor.Subscribe)
  // It would be nice if this message was in a more general location than HcdController

  // This works because we only have one HCD
  val hcdName: String = assemblyContext.info.connections.head.name

  // Start in operations mode - 0 is initial stateMessageCounter value
  def receive: Receive = operationsReceive(0, tromboneHCDIn)

  /**
   * The receive method in operations state.
   *
   * In operations state every 5th AxisUpdate message from the HCD is published as a status event. It sends an AxisStateUpdate message
   * to the event publisher
   *
   * //@param currentStateReceive the source for CurrentState messages
   * @param stateMessageCounter the number of messages received by the diag publisher
   * @param tromboneHCD         the trombone HCD ActorRef as an Option
   *
   * @return Receive partial function
   */
  def operationsReceive(stateMessageCounter: Int, tromboneHCD: Option[ActorRef]): Receive = {
    case cs: CurrentState if cs.configKey == TromboneHCD.axisStateCK =>
      if (stateMessageCounter % operationsSkipCount == 0) publishStateUpdate(cs)
      context.become(operationsReceive(stateMessageCounter + 1, tromboneHCD))

    case cs: CurrentState if cs.configKey == TromboneHCD.axisStatsCK => // No nothing
    case TimeForAxisStats(_) => // Do nothing, here so it doesn't make an error
    case OperationsState => // Already in operations mode

    case DiagnosticState =>
      // If the DiagnosticMode message is received, begin collecting axis stats messages based on a timer and query to HCD
      // The cancelToken allows turning off the timer when
      val cancelToken: Cancellable = scheduleOnce(localTimeNow.plusSeconds(diagnosticAxisStatsPeriod), self, TimeForAxisStats(diagnosticAxisStatsPeriod))
      context.become(diagnosticReceive(stateMessageCounter, tromboneHCD, cancelToken))

    case UpdateTromboneHCD(tromboneHCDUpdate) =>
      context.become(operationsReceive(stateMessageCounter, tromboneHCDUpdate))

    case location: Location =>
      location match {
        case rloc: ResolvedAkkaLocation =>
          if (rloc.connection.name == hcdName) {
            log.info(s"operationsReceive updated actorRef: ${rloc.actorRef}")
            val newHcdActorRef = rloc.actorRef
            newHcdActorRef.foreach(_ ! HcdController.Subscribe)
            context.become(operationsReceive(stateMessageCounter, newHcdActorRef))
          }
        case Unresolved(connection) =>
          if (connection.name == hcdName) {
            log.info("operationsReceive got unresolve for trombone HCD")
            context.become(operationsReceive(stateMessageCounter, None))
          }
        case UnTrackedLocation(connection) =>
          if (connection.name == hcdName) {
            log.info("operationsReceive got untrack for trombone HCD")
            context.become(operationsReceive(stateMessageCounter, None))
          }
        case h: ResolvedHttpLocation => // Do Nothing
        case t: ResolvedTcpLocation  => // Do Nothing
      }

    case x => log.error(s"DiagPublisher:operationsReceive received an unexpected message: $x")
  }

  /**
   * The receive method in diagnostic state
   *
   * //@param currentStateReceive the source for CurrentState messages
   * @param stateMessageCounter the number of messages received by the diag publisher
   * @param tromboneHCD         the trombone HCD ActorRef as an Option
   * @param cancelToken         a token that allows the current timer to be cancelled
   *
   * @return Receive partial function
   */
  def diagnosticReceive(stateMessageCounter: Int, tromboneHCD: Option[ActorRef], cancelToken: Cancellable): Receive = {
    case cs: CurrentState if cs.configKey == TromboneHCD.axisStateCK =>
      if (stateMessageCounter % diagnosticSkipCount == 0) publishStateUpdate(cs)
      context.become(diagnosticReceive(stateMessageCounter + 1, tromboneHCD, cancelToken))

    case cs: CurrentState if cs.configKey == TromboneHCD.axisStatsCK =>
      // Here when a CurrentState is received with the axisStats configKey, the axis statistics are published as an event
      publishStatsUpdate(cs)

    case TimeForAxisStats(periodInSeconds) =>
      // Here, every period, an Axis statistics is requested, which is then pubilshed for diagnostics when the response arrives
      // This shows how to periodically query the HCD
      tromboneHCD.foreach(_ ! GetAxisStats)
      val canceltoken: Cancellable = scheduleOnce(localTimeNow.plusSeconds(periodInSeconds), self, TimeForAxisStats(periodInSeconds))
      context.become(diagnosticReceive(stateMessageCounter, tromboneHCD, canceltoken))

    case DiagnosticState => // Do nothing, already in this mode

    case OperationsState =>
      // Switch to Operations State
      cancelToken.cancel
      context.become(operationsReceive(stateMessageCounter, tromboneHCD))

    case UpdateTromboneHCD(tromboneHCDUpdate) =>
      // The actor ref of the trombone HCD has changed
      context.become(diagnosticReceive(stateMessageCounter, tromboneHCDUpdate, cancelToken))

    case location: Location =>
      location match {
        case rloc: ResolvedAkkaLocation =>
          if (rloc.connection.name == hcdName) {
            log.info(s"diagnosticReceive updated actorRef: ${rloc.actorRef}")
            // Need to subscribe to CurrentState
            val newHcdActorRef = rloc.actorRef
            newHcdActorRef.foreach(_ ! HcdController.Subscribe)
            context.become(diagnosticReceive(stateMessageCounter, newHcdActorRef, cancelToken))
          }
        case Unresolved(connection) =>
          if (connection.name == hcdName) {
            log.info("diagnosticReceive got unresolve for trombone HCD")
            context.become(diagnosticReceive(stateMessageCounter, None, cancelToken))
          }
        case UnTrackedLocation(connection) =>
          if (connection.name == hcdName) {
            log.info("diagnosticReceive got untrack for trombone HCD")
            context.become(diagnosticReceive(stateMessageCounter, None, cancelToken))
          }
        case h: ResolvedHttpLocation => // Do Nothing
        case t: ResolvedTcpLocation  => // Do Nothing
      }

    case x => log.error(s"DiagPublisher:diagnosticReceive received an unexpected message: $x")
  }

  private def publishStateUpdate(cs: CurrentState): Unit = {
    log.debug(s"publish diag state: $cs")
    eventPublisher.foreach(_ ! AxisStateUpdate(cs(axisNameKey), cs(positionKey), cs(stateKey), cs(inLowLimitKey), cs(inHighLimitKey), cs(inHomeKey)))
  }

  private def publishStatsUpdate(cs: CurrentState): Unit = {
    log.debug("publish diag stats")
    eventPublisher.foreach(_ ! AxisStatsUpdate(cs(axisNameKey), cs(datumCountKey), cs(moveCountKey), cs(homeCountKey), cs(limitCountKey), cs(successCountKey), cs(failureCountKey), cs(cancelCountKey)))
  }

}

object DiagPublisher {

  def props(assemblyContext: AssemblyContext, tromboneHCD: Option[ActorRef], eventPublisher: Option[ActorRef]): Props =
    Props(classOf[DiagPublisher], assemblyContext, tromboneHCD, eventPublisher)

  /**
   * Internal messages used by diag publisher
   */
  trait DiagPublisherMessages

  final case class TimeForAxisStats(periodInseconds: Int) extends DiagPublisherMessages

  final case object DiagnosticState extends DiagPublisherMessages

  final case object OperationsState extends DiagPublisherMessages

  val diagnosticSkipCount = 2
  val operationsSkipCount = 5

  // Following are in units of seconds - could be in a configuration file
  val diagnosticAxisStatsPeriod = 1
}
