package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.TromboneStateActor.TromboneState
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus.{Completed, Error, NoLongerValid}
import csw.services.ccs.HcdController
import csw.services.ccs.SequentialExecutor.{CommandStart, StopCurrentCommand}
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.util.config.Configurations.SetupConfig

/**
 * TMT Source Code: 10/21/16.
 */
class DatumCommand(sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]) extends Actor with ActorLogging {
  import TromboneCommandHandler._
  import TromboneStateActor._

  // Not using stateReceive since no state updates are needed here only writes
  def receive: Receive = {
    case CommandStart =>
      if (startState.cmd.head == cmdUninitialized) {
        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow datum"))
      } else {
        val mySender = sender()
        sendState(SetState(cmdItem(cmdBusy), moveItem(moveIndexing), startState.sodiumLayer, startState.nss))
        tromboneHCD ! HcdController.Submit(SetupConfig(axisDatumCK))
        TromboneCommandHandler.executeMatch(context, idleMatcher, tromboneHCD, Some(mySender)) {
          case Completed =>
            sendState(SetState(cmdReady, moveIndexed, sodiumLayer = false, nss = false))
          case Error(message) =>
            log.error(s"Data command match failed with error: $message")
        }
      }
    case StopCurrentCommand =>
      log.debug(">>  DATUM STOPPED")
      tromboneHCD ! HcdController.Submit(cancelSC)
  }

  private def sendState(setState: SetState) = stateActor.foreach(_ ! setState)
}

object DatumCommand {
  def props(sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]): Props =
    Props(classOf[DatumCommand], sc, tromboneHCD, startState, stateActor)
}
