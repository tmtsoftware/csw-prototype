package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import csw.examples.vslice.assembly.FollowActor.SetZenithAngle
import csw.examples.vslice.assembly.FollowCommand.StopFollowing
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus._
import csw.services.ccs.SequentialExecutor.{CommandStart, ExecuteOne, StopCurrentCommand}
import csw.services.ccs.MultiStateMatcherActor.StartMatch
import csw.services.ccs.{DemandMatcher, MultiStateMatcherActor, StateMatcher}
import csw.services.ccs.Validation.{RequiredHCDUnavailableIssue, UnsupportedCommandInStateIssue, WrongInternalStateIssue}
import csw.services.events.EventService
import csw.services.loc.LocationService._
import csw.services.loc.LocationSubscriberClient
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.DemandState

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * TMT Source Code: 9/21/16.
 */
class TromboneCommandHandler(ac: AssemblyContext, tromboneHCDIn: Option[ActorRef], allEventPublisher: Option[ActorRef])
    extends Actor with ActorLogging with LocationSubscriberClient with TromboneStateClient {

  import TromboneStateActor._
  import TromboneCommandHandler._
  import ac._
  implicit val system: ActorSystem = context.system

  //override val prefix = ac.info.prefix
  private val badHCDReference = context.system.deadLetters

  private var tromboneHCD: ActorRef = tromboneHCDIn.getOrElse(badHCDReference)
  private def isHCDAvailable: Boolean = tromboneHCD != badHCDReference

  private val badEventService = None
  var eventService: Option[EventService] = badEventService

  // Set the default evaluation for use with the follow command
  private var setElevationItem = naElevation(calculationConfig.defaultInitialElevation)

  // The actor for managing the persistent assembly state as defined in the spec is here, it is passed to each command
  private val tromboneStateActor = context.actorOf(TromboneStateActor.props())

  def receive: Receive = noFollowReceive()

  def handleLocations(location: Location): Unit = {
    location match {

      case l: ResolvedAkkaLocation =>
        log.debug(s"CommandHandler receive an actorRef: ${l.actorRef}")
        tromboneHCD = l.actorRef.getOrElse(badHCDReference)

      case t: ResolvedTcpLocation =>
        log.info(s"Received TCP Location: ${t.connection} from ${sender()}")
        // Verify that it is the event service
        if (t.connection == EventService.eventServiceConnection()) {
          log.info(s"Subscriber received connection: $t from ${sender()}")
          // Setting var here!
          eventService = Some(EventService.get(t.host, t.port))
          log.info(s"Event Service at: $eventService")
        }

      case u: Unresolved =>
        log.info(s"Unresolved: ${u.connection}")
        if (u.connection == EventService.eventServiceConnection()) eventService = badEventService
        if (u.connection.componentId == ac.hcdComponentId) tromboneHCD = badHCDReference

      case default =>
        log.info(s"CommandHandler received some other location: $default")
    }
  }

  def noFollowReceive(): Receive = stateReceive orElse {

    case l: Location =>
      handleLocations(l)

    case ExecuteOne(sc, commandOriginator) =>

      sc.configKey match {
        case ac.initCK =>
          log.info("Init not fully implemented -- only sets state ready!")
          tromboneStateActor ! SetState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
          commandOriginator.foreach(_ ! Completed)

        case ac.datumCK =>
          if (isHCDAvailable) {
            log.info(s"Datums State: $currentState")
            val datumActorRef = context.actorOf(DatumCommand.props(sc, tromboneHCD, currentState, Some(tromboneStateActor)))
            context.become(actorExecutingReceive(datumActorRef, commandOriginator))
            self ! CommandStart
          } else hcdNotAvailableResponse(commandOriginator)

        case ac.moveCK =>
          if (isHCDAvailable) {
            val moveActorRef = context.actorOf(MoveCommand.props(ac, sc, tromboneHCD, currentState, Some(tromboneStateActor)))
            context.become(actorExecutingReceive(moveActorRef, commandOriginator))
            self ! CommandStart
          } else hcdNotAvailableResponse(commandOriginator)

        case ac.positionCK =>
          if (isHCDAvailable) {
            val positionActorRef = context.actorOf(PositionCommand.props(ac, sc, tromboneHCD, currentState, Some(tromboneStateActor)))
            context.become(actorExecutingReceive(positionActorRef, commandOriginator))
            self ! CommandStart
          } else hcdNotAvailableResponse(commandOriginator)

        case ac.stopCK =>
          commandOriginator.foreach(_ ! NoLongerValid(WrongInternalStateIssue("Trombone assembly must be executing a command to use stop")))

        case ac.setAngleCK =>
          commandOriginator.foreach(_ ! NoLongerValid(WrongInternalStateIssue("Trombone assembly must be following for setAngle")))

        case ac.setElevationCK =>
          // Setting the elevation state here for a future follow command
          setElevationItem = sc(ac.naElevationKey)
          log.info("Setting elevation to: " + setElevationItem)
          // Note that units have already been verified here
          val setElevationActorRef = context.actorOf(SetElevationCommand.props(ac, sc, tromboneHCD, currentState, Some(tromboneStateActor)))
          context.become(actorExecutingReceive(setElevationActorRef, commandOriginator))
          self ! CommandStart

        case ac.followCK =>
          if (cmd(currentState) == cmdUninitialized
            || (move(currentState) != moveIndexed && move(currentState) != moveMoving)
            || !sodiumLayer(currentState)) {
            commandOriginator.foreach(_ ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(currentState)}/${move(currentState)}/${sodiumLayer(currentState)} does not allow follow")))
          } else {
            // No state set during follow
            // At this point, parameters have been checked so direct access is okay
            val nssItem = sc(ac.nssInUseKey)

            log.info("Set elevation is: " + setElevationItem)

            // The event publisher may be passed in (XXX FIXME? pass in eventService)
            val props = FollowCommand.props(ac, setElevationItem, nssItem, Some(tromboneHCD), allEventPublisher, eventService.get)
            // Follow command runs the trombone when following
            val followCommandActor = context.actorOf(props)
            log.info("Going to followReceive")
            context.become(followReceive(eventService.get, followCommandActor))
            // Note that this is where sodiumLayer is set allowing other commands that require this state
            tromboneStateActor ! SetState(cmdContinuous, moveMoving, sodiumLayer(currentState), nssItem.head)
            commandOriginator.foreach(_ ! Completed)
          }

        case otherCommand =>
          log.error(s"TromboneCommandHandler:noFollowReceive received an unknown command: $otherCommand")
          commandOriginator.foreach(_ ! Invalid(UnsupportedCommandInStateIssue(s"""Trombone assembly does not support the command \"${otherCommand.prefix}\" in the current state.""")))
      }

    case x => log.error(s"TromboneCommandHandler:noFollowReceive received an unknown message: $x")
  }

  def hcdNotAvailableResponse(commandOriginator: Option[ActorRef]): Unit = {
    commandOriginator.foreach(_ ! NoLongerValid(RequiredHCDUnavailableIssue(s"${ac.hcdComponentId} is not available")))
  }

  def followReceive(eventService: EventService, followActor: ActorRef): Receive = stateReceive orElse {
    case ExecuteOne(sc, commandOriginator) =>
      sc.configKey match {
        case ac.datumCK | ac.moveCK | ac.positionCK | ac.followCK | ac.setElevationCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly cannot be following for datum, move, position, setElevation, and follow")))

        case ac.setAngleCK =>
          // Unclear what to really do with state here
          // Everything else is the same
          tromboneStateActor ! SetState(cmdBusy, move(currentState), sodiumLayer(currentState), nss(currentState))

          // At this point, parameters have been checked so direct access is okay
          // Send the SetAngle to the follow actor
          val zenithAngleItem = sc(ac.zenithAngleKey)
          followActor ! SetZenithAngle(zenithAngleItem)
          executeMatch(context, idleMatcher, tromboneHCD, commandOriginator) {
            case Completed =>
              tromboneStateActor ! SetState(cmdContinuous, move(currentState), sodiumLayer(currentState), nss(currentState))
            case Error(message) =>
              log.error(s"setElevation command failed with message: $message")
          }

        case ac.stopCK =>
          // Stop the follower
          log.debug(s"Stop received while following")
          followActor ! StopFollowing
          tromboneStateActor ! SetState(cmdReady, moveIndexed, sodiumLayer(currentState), nss(currentState))

          // Go back to no follow state
          context.become(noFollowReceive())
          commandOriginator.foreach(_ ! Completed)
      }

    case x => log.error(s"TromboneCommandHandler:followReceive received an unknown message: $x")
  }

  def actorExecutingReceive(currentCommand: ActorRef, commandOriginator: Option[ActorRef]): Receive = stateReceive orElse {
    case CommandStart =>
      import context.dispatcher
      implicit val t = Timeout(5.seconds)

      // Execute the command actor asynchronously, pass the command status back, kill the actor and go back to waiting
      for {
        cs <- (currentCommand ? CommandStart).mapTo[CommandStatus]
      } {
        commandOriginator.foreach(_ ! cs)
        currentCommand ! PoisonPill
        self ! CommandDone
      }

    case SetupConfig(ac.stopCK, _) =>
      log.debug("actorExecutingReceive: Stop CK")
      closeDownMotionCommand(currentCommand, commandOriginator)

    case ExecuteOne(SetupConfig(ac.stopCK, _), _) =>
      log.debug("actorExecutingReceive: ExecuteOneStop")
      closeDownMotionCommand(currentCommand, commandOriginator)

    case CommandDone =>
      context.become(noFollowReceive())

    case x => log.error(s"TromboneCommandHandler:actorExecutingReceive received an unknown message: $x")
  }

  private def closeDownMotionCommand(currentCommand: ActorRef, commandOriginator: Option[ActorRef]): Unit = {
    currentCommand ! StopCurrentCommand
    currentCommand ! PoisonPill
    context.become(noFollowReceive())
    commandOriginator.foreach(_ ! Cancelled)
  }

}

object TromboneCommandHandler {

  def props(assemblyContext: AssemblyContext, tromboneHCDIn: Option[ActorRef], allEventPublisher: Option[ActorRef]) =
    Props(new TromboneCommandHandler(assemblyContext, tromboneHCDIn, allEventPublisher))

  def executeMatch(context: ActorContext, stateMatcher: StateMatcher, currentStateSource: ActorRef, replyTo: Option[ActorRef] = None,
                   timeout: Timeout = Timeout(5.seconds))(codeBlock: PartialFunction[CommandStatus, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    for {
      cmdStatus <- (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus]
    } {
      codeBlock(cmdStatus)
      replyTo.foreach(_ ! cmdStatus)
    }
  }

  def idleMatcher: DemandMatcher = DemandMatcher(DemandState(axisStateCK).add(stateKey -> TromboneHCD.AXIS_IDLE))

  def posMatcher(position: Int): DemandMatcher =
    DemandMatcher(DemandState(axisStateCK).madd(stateKey -> TromboneHCD.AXIS_IDLE, positionKey -> position))

  // message sent to self when a command completes
  private case object CommandDone
}

