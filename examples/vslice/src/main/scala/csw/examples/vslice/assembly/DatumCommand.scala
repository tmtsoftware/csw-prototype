package csw.examples.vslice

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.{TromboneCommandHandler, TromboneStateHandler}
import csw.examples.vslice.assembly.TromboneStateHandler.{TromboneState, _}
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.ccs.CommandStatus2.{Completed, Error, NoLongerValid}
import csw.services.ccs.HcdController
import csw.services.ccs.SequentialExecution.SequentialExecutor.{CommandStart, StopCurrentCommand}
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.util.config.Configurations.SetupConfig

/**
 * TMT Source Code: 10/21/16.
 */
class DatumCommandActor(sc: SetupConfig, tromboneHCD: ActorRef) extends Actor with ActorLogging with TromboneStateHandler {
  import TromboneStateHandler._
  import TromboneCommandHandler._

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
        TromboneCommandHandler.executeMatch(context, idleMatcher, tromboneHCD, Some(mySender)) {
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

object DatumCommandActor {
  //def DatumCommand(sc: SetupConfig, tromboneHCD: ActorRef): ActorRef = context.actorOf(Props(new DatumCommandActor(sc, tromboneHCD, tromboneState)))

  def props(sc: SetupConfig, tromboneHCD: ActorRef): Props = Props(classOf[DatumCommandActor], tromboneHCD)
}
