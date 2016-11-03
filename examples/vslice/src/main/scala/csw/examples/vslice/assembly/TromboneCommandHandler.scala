package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import csw.examples.vslice.assembly.FollowActor.{SetElevation, SetZenithAngle}
import csw.examples.vslice.assembly.FollowCommand.StopFollowing
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.SequentialExecution.SequentialExecutor.{CommandStart, ExecuteOne, StopCurrentCommand}
import csw.services.ccs.StateMatchers.MultiStateMatcherActor.StartMatch
import csw.services.ccs.StateMatchers.{DemandMatcher, MultiStateMatcherActor, StateMatcher}
import csw.services.ccs.Validation.{UnsupportedCommandInStateIssue, WrongInternalStateIssue}
import csw.services.events.EventServiceSettings
import csw.services.loc.LocationService._
import csw.services.loc.LocationSubscriberClient
import csw.services.log.PrefixedActorLogging
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.DemandState

import scala.concurrent.duration._

/**
 * TMT Source Code: 9/21/16.
 */
class TromboneCommandHandler(ac: AssemblyContext, tromboneHCDIn: Option[ActorRef], allEventPublisher: Option[ActorRef]) extends Actor with ActorLogging with LocationSubscriberClient with TromboneStateClient {

  import TromboneStateActor._
  import TromboneCommandHandler._
  import ac._

  //override val prefix = ac.info.prefix

  var tromboneHCD: ActorRef = tromboneHCDIn.getOrElse(context.system.deadLetters)
  // Set the default evaluation for use with the follow command
  var setElevationItem = naElevation(calculationConfig.defaultInitialElevation)

  //val currentStateReceiver = context.actorOf(CurrentStateReceiver.props)
  //currentStateReceiver ! AddPublisher(tromboneHCD)
  log.info("System  is: " + context.system)

  // The actor for managing the persistent assembly state as defined in the spec is here, it is passed to each command
  val tromboneStateActor = context.actorOf(TromboneStateActor.props())

  def receive = noFollowReceive()

  def noFollowReceive(): Receive = stateReceive orElse {

    case l: Location =>
      log.info("CommandHandler: " + l)
      lookatLocations(l)

    case ExecuteOne(sc, commandOriginator) =>

      sc.configKey match {
        case ac.initCK =>
          log.info("Init not yet implemented")

        case ac.datumCK =>
          val datumActorRef = context.actorOf(DatumCommand.props(sc, tromboneHCD, currentState, Some(tromboneStateActor)))
          context.become(actorExecutingReceive(datumActorRef, commandOriginator))
          self ! CommandStart

        case ac.moveCK =>
          log.info("Current state: " + currentState)
          val moveActorRef = context.actorOf(MoveCommand.props(ac, sc, tromboneHCD, currentState, Some(tromboneStateActor)))
          context.become(actorExecutingReceive(moveActorRef, commandOriginator))
          self ! CommandStart

        case ac.positionCK =>
          val positionActorRef = context.actorOf(PositionCommand.props(ac, sc, tromboneHCD, currentState, Some(tromboneStateActor)))
          context.become(actorExecutingReceive(positionActorRef, commandOriginator))
          self ! CommandStart

        case ac.stopCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly must be executing a command to use stop")))

        case ac.setAngleCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly must be following for setAngle")))

        case ac.setElevationCK =>
          // Setting the elevation state here for a future follow command
          setElevationItem = sc(ac.naElevationKey)
          log.info("Setting elevation to: " + setElevationItem)
          // Note that units have already been verified here
          val setElevationActorRef = context.actorOf(SetElevationCommand.props(ac, sc, tromboneHCD, currentState, Some(tromboneStateActor)))
          context.become(actorExecutingReceive(setElevationActorRef, commandOriginator))
          self ! CommandStart

        case ac.followCK =>
          if (cmd(currentState) == cmdUninitialized || (move(currentState) != moveIndexed && move(currentState) != moveMoving) || !sodiumLayer(currentState)) {
            commandOriginator.foreach(_ ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(currentState)}/${move(currentState)}/${sodiumLayer(currentState)} does not allow follow")))
          } else {
            // No state set during follow
            // At this point, parameters have been checked so direct access is okay
            val nssItem = sc(ac.nssInUseKey)

            log.info("Set elevation is: " + setElevationItem)

            // The event publisher may be passed in (XXX FIXME? pass in eventService)
            val props = FollowCommand.props(ac, setElevationItem, nssItem, Some(tromboneHCD), allEventPublisher, eventService = None)
            // Follow command runs the trombone when following
            val followCommandActor = context.actorOf(props)
            context.become(followReceive(followCommandActor))
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

  def lookatLocations(location: Location): Unit = {
    location match {
      case l: ResolvedAkkaLocation =>
        log.info(s"Got actorRef: ${l.actorRef}")
        tromboneHCD = l.actorRef.getOrElse(context.system.deadLetters)
      case h: ResolvedHttpLocation =>
        log.info(s"HTTP: ${h.connection}")
      case t: ResolvedTcpLocation =>
        log.info(s"Received TCP Location: ${t.connection}")
      case u: Unresolved =>
        log.info(s"Unresolved: ${u.connection}")
      case ut: UnTrackedLocation =>
        log.info(s"UnTracked: ${ut.connection}")
    }
  }

  def followReceive(followActor: ActorRef): Receive = stateReceive orElse {
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
          log.info(s"Just received the stop")
          followActor ! StopFollowing
          tromboneStateActor ! SetState(cmdReady, moveIndexed, sodiumLayer(currentState), nss(currentState))

          // Go back to no follow state
          context.become(noFollowReceive())
          commandOriginator.foreach(_ ! Completed)
      }

    case commandStop =>

  }

  def actorExecutingReceive(currentCommand: ActorRef, commandOriginator: Option[ActorRef]): Receive = stateReceive orElse {
    case CommandStart =>
      import context.dispatcher
      implicit val t = Timeout(5.seconds)

      // Execute the command actor asynchronously, pass the command status back, kill the actor and go back to waiting
      for {
        cs <- (currentCommand ? CommandStart).mapTo[CommandStatus2]
      } {
        commandOriginator.foreach(_ ! cs)
        currentCommand ! PoisonPill
        context.become(noFollowReceive())
      }

    case StopCurrentCommand =>
      // This sends the Stop sc to the HCD
      log.debug("actorExecutingReceive STOP STOP")
      closeDownMotionCommand(currentCommand, commandOriginator)

    case SetupConfig(ac.stopCK, _) =>
      log.debug("actorExecutingReceive: Stop CK")
      closeDownMotionCommand(currentCommand, commandOriginator)

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
                   timeout: Timeout = Timeout(5.seconds))(codeBlock: PartialFunction[CommandStatus2, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    for {
      cmdStatus <- (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus2]
    } {
      codeBlock(cmdStatus)
      replyTo.foreach(_ ! cmdStatus)
    }
  }

  def idleMatcher: DemandMatcher = DemandMatcher(DemandState(axisStateCK).add(stateKey -> TromboneHCD.AXIS_IDLE))

  def posMatcher(position: Int): DemandMatcher =
    DemandMatcher(DemandState(axisStateCK).madd(stateKey -> TromboneHCD.AXIS_IDLE, positionKey -> position))

}

