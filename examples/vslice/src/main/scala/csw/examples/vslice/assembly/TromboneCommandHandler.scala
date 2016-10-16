package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import csw.examples.vslice.assembly.AssemblyContext.TromboneControlConfig
import csw.examples.vslice.assembly.FollowActor.{SetElevation, SetZenithAngle}
import csw.examples.vslice.assembly.FollowCommand.StopFollowing
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus2._
import csw.services.ccs.HcdController
import csw.services.ccs.SequentialExecution.SequentialExecutor.{CommandStart, ExecuteOne, StopCurrentCommand}
import csw.services.ccs.StateMatchers.MultiStateMatcherActor.StartMatch
import csw.services.ccs.StateMatchers.{DemandMatcher, MultiStateMatcherActor, StateMatcher}
import csw.services.ccs.Validation.{UnsupportedCommandInStateIssue, WrongInternalStateIssue}
import csw.services.events.EventServiceSettings
import csw.services.loc.LocationService._
import csw.services.loc.TrackerSubscriberClient
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.DemandState
import csw.util.config.UnitsOfMeasure.encoder

import scala.concurrent.duration._

/**
 * TMT Source Code: 9/21/16.
 */
class TromboneCommandHandler(ac: AssemblyContext, tromboneHCDIn: Option[ActorRef], allEventPublisher: Option[ActorRef]) extends Actor with ActorLogging with TrackerSubscriberClient with TromboneStateHandler {

  import TromboneStateHandler._

  var tromboneHCD: ActorRef = tromboneHCDIn.getOrElse(context.system.deadLetters)

  //val currentStateReceiver = context.actorOf(CurrentStateReceiver.props)
  //currentStateReceiver ! AddPublisher(tromboneHCD)

  // Indicate we want location updates
  subscribeToLocationUpdates()

  val testEventServiceSettings = EventServiceSettings("localhost", 7777)

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
          val datumActorRef = DatumCommand(sc, tromboneHCD)
          context.become(actorExecutingReceive(datumActorRef, commandOriginator))
          self ! CommandStart

        case ac.moveCK =>
          val moveActorRef = MoveCommand(sc, tromboneHCD)
          context.become(actorExecutingReceive(moveActorRef, commandOriginator))
          self ! CommandStart

        case ac.positionCK =>
          val positionActorRef = PositionCommand(sc, tromboneHCD)
          context.become(actorExecutingReceive(positionActorRef, commandOriginator))
          self ! CommandStart

        case ac.stopCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly must be executing a command to use stop")))

        case ac.setAngleCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly must be following for setAngle")))

        case ac.setElevationCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly must be following for setElevation")))

        case ac.followCK =>
          if (cmd == cmdUninitialized || (move != moveIndexed && move != moveMoving) || !sodiumLayer) {
            commandOriginator.foreach(_ ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of $cmd/$move does not allow follow")))
          } else {
            // No state set during follow
            // At this point, parameters have been checked so direct access is okay
            val nssItem = sc(ac.nssInUseKey)

            // The event publisher may be passed in
            val props = FollowCommand.props(ac, nssItem, Some(tromboneHCD), allEventPublisher, Some(testEventServiceSettings))
            // Follow command runs the trombone when following
            val followCommandActor = context.actorOf(props)
            context.become(followReceive(followCommandActor))
            // Note that this is where sodiumLayer is set allowing other commands that require this state
            state(cmd = cmdContinuous, move = moveMoving, nss = nssItem.head)
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
        log.info(s"Received TCP Location: ${t.connection}" )
      case u: Unresolved =>
        log.info(s"Unresolved: ${u.connection}")
      case ut: UnTrackedLocation =>
        log.info(s"UnTracked: ${ut.connection}")
    }
  }

  def followReceive(followActor: ActorRef): Receive = stateReceive orElse {
    case ExecuteOne(sc, commandOriginator) =>
      sc.configKey match {
        case ac.datumCK | ac.moveCK | ac.positionCK | ac.followCK =>
          commandOriginator.foreach(_ ! Invalid(WrongInternalStateIssue("Trombone assembly cannot be following for datum, move, position, and follow")))

        case ac.setElevationCK =>
          // Unclear what to really do with state here
          // Everything else is the same
          state(cmd = cmdBusy)

          // At this point, parameters have been checked so direct access is okay
          // Send the SetElevation to the follow actor
          val elevationItem = sc(ac.naElevationKey)
          followActor ! SetElevation(elevationItem)
          executeMatch(context, idleMatcher, tromboneHCD, commandOriginator) {
            case Completed =>
              state(cmd = cmdContinuous)
            case Error(message) =>
              log.error(s"setElevation command failed with message: $message")
          }

        case ac.setAngleCK =>
          // Unclear what to really do with state here
          // Everything else is the same
          state(cmd = cmdBusy)

          // At this point, parameters have been checked so direct access is okay
          // Send the SetAngle to the follow actor
          val zenithAngleItem = sc(ac.zenithAngleKey)
          followActor ! SetZenithAngle(zenithAngleItem)
          executeMatch(context, idleMatcher, tromboneHCD, commandOriginator) {
            case Completed =>
              state(cmd = cmdContinuous)
            case Error(message) =>
              log.error(s"setElevation command failed with message: $message")
          }

        case ac.stopCK =>
          // Stop the follower
          log.info(s"Just received the stop")
          followActor ! StopFollowing
          state(cmd = cmdReady)
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

  class DatumCommandActor(sc: SetupConfig, tromboneHCD: ActorRef, ts: TromboneState) extends Actor with ActorLogging {

    // Not using stateReceive since no state updates are needed here only writes
    def receive: Receive = {
      case CommandStart =>
        if (cmd == cmdUninitialized) {
          sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of $cmd/$move does not allow datum"))
        } else {
          log.info(s"In Start: $tromboneState")
          val mySender = sender()
          state(cmd = cmdBusy, move = moveIndexing)
          tromboneHCD ! HcdController.Submit(SetupConfig(axisDatumCK))
          executeMatch(context, idleMatcher, tromboneHCD, Some(mySender)) {
            case Completed =>
              state(cmd = cmdReady, move = moveIndexed, sodiumLayer = false, nss = false)
            case Error(message) =>
              println(s"Error: $message")
          }
        }
      case StopCurrentCommand =>
        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>  DATUM STOP STOP")
        tromboneHCD ! HcdController.Submit(cancelSC)
    }
  }

  def DatumCommand(sc: SetupConfig, tromboneHCD: ActorRef): ActorRef =
    context.actorOf(Props(new DatumCommandActor(sc, tromboneHCD, tromboneState)))

  class MoveCommandActor(controlConfig: TromboneControlConfig, sc: SetupConfig, tromboneHCD: ActorRef) extends Actor with ActorLogging {
    def receive = {
      case CommandStart =>
        // Move moves the trombone state in mm but not encoder units
        if (cmd == cmdUninitialized || (move != moveIndexed && move != moveMoving)) {
          sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of $cmd/$move does not allow move"))
        } else {
          val mySender = sender()
          val stagePosition = sc(ac.stagePositionKey)

          // Convert to encoder units from mm
          val encoderPosition = Algorithms.stagePositionToEncoder(controlConfig, stagePosition.head)

          log.info(s"Setting trombone axis to: $encoderPosition")

          val stateMatcher = posMatcher(encoderPosition)
          // Position key is encoder units
          val scOut = SetupConfig(axisMoveCK).add(positionKey -> encoderPosition withUnits encoder)

          state(cmd = cmdBusy, move = moveMoving)
          tromboneHCD ! HcdController.Submit(scOut)
          executeMatch(context, stateMatcher, tromboneHCD, Some(mySender)) {
            case Completed =>
              state(cmd = cmdReady, move = moveIndexed)
            case Error(message) =>
              log.error(s"Move command failed with message: $message")
          }
        }

      case StopCurrentCommand =>
        log.info("Move command -- STOP")
        tromboneHCD ! HcdController.Submit(cancelSC)
    }
  }

  def MoveCommand(sc: SetupConfig, tromboneHCD: ActorRef): ActorRef =
    context.actorOf(Props(new MoveCommandActor(ac.controlConfig, sc, tromboneHCD)))

  class PositionCommandActor(controlConfig: TromboneControlConfig, sc: SetupConfig, tromboneHCD: ActorRef) extends Actor with ActorLogging {

    def receive: Receive = {
      case CommandStart =>
        if (cmd == cmdUninitialized || (move != moveIndexed && move != moveMoving)) {
          sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of $cmd/$move does not allow motion"))
        } else {
          val mySender = sender()

          // Note that units have already been verified here
          val rangeDistance = sc(ac.naRangeDistanceKey)

          // Convert elevation to
          // Convert to encoder units from mm
          val stagePosition = Algorithms.rangeDistanceToStagePosition(rangeDistance.head)
          val encoderPosition = Algorithms.stagePositionToEncoder(controlConfig, stagePosition)

          log.info(s"Using rangeDistance: ${rangeDistance.head} to get stagePosition: $stagePosition to encoder: $encoderPosition")

          val stateMatcher = posMatcher(encoderPosition)
          // Position key is encoder units
          val scOut = SetupConfig(axisMoveCK).add(positionKey -> encoderPosition withUnits encoder)
          state(cmd = cmdBusy, move = moveMoving)
          tromboneHCD ! HcdController.Submit(scOut)

          executeMatch(context, stateMatcher, tromboneHCD, Some(mySender)) {
            case Completed =>
              state(cmd = cmdReady, move = moveIndexed)
            case Error(message) =>
              log.error(s"Position command failed with message: $message")
          }
        }
      case StopCurrentCommand =>
        log.info("Move command -- STOP")
        tromboneHCD ! HcdController.Submit(cancelSC)
    }

    override def postStop() {
      unsubscribeState()
    }
  }

  def PositionCommand(sc: SetupConfig, tromboneHCD: ActorRef): ActorRef = {
    context.actorOf(Props(new PositionCommandActor(ac.controlConfig, sc, tromboneHCD)))
  }

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

object TromboneCommandHandler {

  def props(assemblyContext: AssemblyContext, tromboneHCDIn: Option[ActorRef], allEventPublisher: Option[ActorRef]) =
    Props(new TromboneCommandHandler(assemblyContext, tromboneHCDIn, allEventPublisher))

}