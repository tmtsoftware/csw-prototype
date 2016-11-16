package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.TromboneStateActor._
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
class PositionCommand(ac: AssemblyContext, sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]) extends Actor with ActorLogging {

  import TromboneCommandHandler._
  import TromboneStateActor._

  def receive: Receive = {
    case CommandStart =>
      if (cmd(startState) == cmdUninitialized || (move(startState) != moveIndexed && move(startState) != moveMoving)) {
        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow motion"))
      } else {
        val mySender = sender()

        // Note that units have already been verified here
        val rangeDistance = sc(ac.naRangeDistanceKey)

        // Convert range distance to encoder units from mm
        val stagePosition = Algorithms.rangeDistanceToStagePosition(rangeDistance.head)
        val encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition)

        log.info(s"Using rangeDistance: ${rangeDistance.head} to get stagePosition: $stagePosition to encoder: $encoderPosition")

        val stateMatcher = posMatcher(encoderPosition)
        // Position key is encoder units
        val scOut = SetupConfig(axisMoveCK).add(positionKey -> encoderPosition withUnits encoder)
        sendState(SetState(cmdItem(cmdBusy), moveItem(moveMoving), startState.sodiumLayer, startState.nss))
        tromboneHCD ! HcdController.Submit(scOut)

        executeMatch(context, stateMatcher, tromboneHCD, Some(mySender)) {
          case Completed =>
            sendState(SetState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), startState.nss))
          case Error(message) =>
            log.error(s"Position command match failed with message: $message")
        }
      }
    case StopCurrentCommand =>
      log.debug("Move command -- STOP")
      tromboneHCD ! HcdController.Submit(cancelSC)
  }

  private def sendState(setState: SetState) = stateActor.foreach(_ ! setState)

}

object PositionCommand {

  def props(ac: AssemblyContext, sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]): Props =
    Props(classOf[PositionCommand], ac, sc, tromboneHCD, startState, stateActor)
}
