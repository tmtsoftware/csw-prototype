package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.ccs.CommandStatus2;
import csw.services.ccs.HcdController;
import csw.services.ccs.Validation;
import csw.util.config.Configurations;
import csw.util.config.Configurations.SetupConfig;
import javacsw.services.ccs.JSequentialExecution;
import csw.examples.vsliceJava.assembly.TromboneStateActor.TromboneState;
import csw.examples.vsliceJava.assembly.TromboneStateActor.SetState;
import csw.services.ccs.CommandStatus2.NoLongerValid;

import java.util.Optional;

import static csw.examples.vsliceJava.assembly.TromboneStateActor.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.axisDatumCK;
import static csw.examples.vsliceJava.hcd.TromboneHCD.cancelSC;
import static javacsw.services.ccs.JCommandStatus2.Completed;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class MoveCommand extends AbstractActor {
}


//class MoveCommand(ac: AssemblyContext, sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]) extends Actor with ActorLogging {
//  import TromboneCommandHandler._
//  import TromboneStateActor._
//
//  def receive: Receive = {
//    case CommandStart =>
//      // Move moves the trombone state in mm but not encoder units
//      if (cmd(startState) == cmdUninitialized || (move(startState) != moveIndexed && move(startState) != moveMoving)) {
//        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow move"))
//      } else {
//        val mySender = sender()
//        val stagePosition = sc(ac.stagePositionKey)
//
//        // Convert to encoder units from mm
//        val encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition.head)
//
//        log.info(s"Setting trombone axis to: $encoderPosition")
//
//        val stateMatcher = posMatcher(encoderPosition)
//        // Position key is encoder units
//        val scOut = SetupConfig(axisMoveCK).add(positionKey -> encoderPosition withUnits encoder)
//
//        sendState(SetState(cmdItem(cmdBusy), moveItem(moveMoving), startState.sodiumLayer, startState.nss))
//        tromboneHCD ! HcdController.Submit(scOut)
//        executeMatch(context, stateMatcher, tromboneHCD, Some(mySender)) {
//          case Completed =>
//            sendState(SetState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), startState.nss))
//          case Error(message) =>
//            log.error(s"Move command match failed with message: $message")
//        }
//      }
//
//    case StopCurrentCommand =>
//      log.info("Move command -- STOP")
//      tromboneHCD ! HcdController.Submit(cancelSC)
//  }
//
//  private def sendState(setState: SetState) = stateActor.foreach(_ ! setState)
//}
//
//object MoveCommand {
//  def props(ac: AssemblyContext, sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]): Props =
//    Props(classOf[MoveCommand], ac, sc, tromboneHCD, startState, stateActor)
//}
