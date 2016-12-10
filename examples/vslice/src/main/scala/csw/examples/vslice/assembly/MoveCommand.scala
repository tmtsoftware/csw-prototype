package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.TromboneStateActor.TromboneState
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus.{Completed, Error, NoLongerValid}
import csw.services.ccs.HcdController
import csw.services.ccs.SequentialExecutor.{CommandStart, StopCurrentCommand}
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.util.config.Configurations.SetupConfig
import csw.util.config.UnitsOfMeasure.encoder

/**
 * TMT Source Code: 10/22/16.
 */
class MoveCommand(ac: AssemblyContext, sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]) extends Actor with ActorLogging {
  import TromboneCommandHandler._
  import TromboneStateActor._

  def receive: Receive = {
    case CommandStart =>
      // Move moves the trombone state in mm but not encoder units
      if (cmd(startState) == cmdUninitialized || (move(startState) != moveIndexed && move(startState) != moveMoving)) {
        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow move"))
      } else {
        val mySender = sender()
        val stagePosition = sc(ac.stagePositionKey)

        // Convert to encoder units from mm
        val encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition.head)

        log.info(s"Setting trombone axis to: $encoderPosition")

        val stateMatcher = posMatcher(encoderPosition)
        // Position key is encoder units
        val scOut = SetupConfig(axisMoveCK).add(positionKey -> encoderPosition withUnits encoder)

        sendState(SetState(cmdItem(cmdBusy), moveItem(moveMoving), startState.sodiumLayer, startState.nss))
        tromboneHCD ! HcdController.Submit(scOut)
        executeMatch(context, stateMatcher, tromboneHCD, Some(mySender)) {
          case Completed =>
            sendState(SetState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), startState.nss))
          case Error(message) =>
            log.error(s"Move command match failed with message: $message")
        }
      }

    case StopCurrentCommand =>
      log.info("Move command -- STOP")
      tromboneHCD ! HcdController.Submit(cancelSC)
  }

  private def sendState(setState: SetState): Unit = {
    log.debug(s"Move send state: $setState")
    stateActor.foreach(_ ! setState)
  }
}

object MoveCommand {
  def props(ac: AssemblyContext, sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]): Props =
    Props(classOf[MoveCommand], ac, sc, tromboneHCD, startState, stateActor)
}
