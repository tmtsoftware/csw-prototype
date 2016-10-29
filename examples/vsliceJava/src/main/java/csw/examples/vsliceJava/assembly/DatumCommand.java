package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import csw.services.ccs.CommandStatus2;
import csw.services.ccs.Validation;
import csw.util.config.Configurations;
import csw.util.config.Configurations.SetupConfig;
import javacsw.services.ccs.JSequentialExecution;
import csw.examples.vsliceJava.assembly.TromboneStateActor.TromboneState;

import java.util.Optional;

import static csw.examples.vsliceJava.hcd.TromboneHCD.axisDatumCK;
import static csw.examples.vsliceJava.hcd.TromboneHCD.cancelSC;
import static javacsw.services.ccs.JCommandStatus2.Completed;

///**
// * TMT Source Code: 10/21/16.
// */
//class DatumCommand(sc: SetupConfig, tromboneHCD: ActorRef, startState: TromboneState, stateActor: Option[ActorRef]) extends Actor with ActorLogging {
//  import TromboneCommandHandler._
//  import TromboneStateActor._
//
//  val prefix = "NFIRAOS.cc.lgsTrombone"
//
//  // Not using stateReceive since no state updates are needed here only writes
//  def receive: Receive = {
//    case CommandStart =>
//      if (startState.cmd.head == cmdUninitialized) {
//        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow datum"))
//      } else {
//        val mySender = sender()
//        sendState(SetState(cmdItem(cmdBusy), moveItem(moveIndexing), startState.sodiumLayer, startState.nss))
//        tromboneHCD ! HcdController.Submit(SetupConfig(axisDatumCK))
//        TromboneCommandHandler.executeMatch(context, idleMatcher, tromboneHCD, Some(mySender)) {
//          case Completed =>
//            sendState(SetState(cmdReady, moveIndexed, sodiumLayer = false, nss = false))
//          case Error(message) =>
//            log.error(s"Data command match failed with error: $message")
//        }
//      }
//    case StopCurrentCommand =>
//      log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>  DATUM STOPPED")
//      tromboneHCD ! HcdController.Submit(cancelSC)
//  }
//
//  private def sendState(setState: SetState) = stateActor.foreach(_ ! setState)
//}


@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DatumCommand extends AbstractActor {

  private static final String prefix = "NFIRAOS.cc.lgsTrombone";

  private final SetupConfig sc;
  private final ActorRef tromboneHCD;
  private final TromboneState startState;
  private final Optional<ActorRef> stateActor;

  private DatumCommand(SetupConfig sc, ActorRef tromboneHCD, TromboneState startState, Optional<ActorRef> stateActor) {
    this.sc = sc;
    this.tromboneHCD = tromboneHCD;
    this.startState = startState;
    this.stateActor = stateActor;

    // Not using stateReceive since no state updates are needed here only writes
    receive(ReceiveBuilder.
      matchEquals(JSequentialExecution.CommandStart()), t -> {
      if (cmd().equals(cmdUninitialized)) {
        sender().tell(new CommandStatus2.NoLongerValid(new Validation.WrongInternalStateIssue("Assembly state of "
          + cmd() + "/" + move() + " does not allow datum"));
      } else {
        log.info("In Start: " + tromboneState);
        ActorRef mySender = sender();
        state(cmdBusy, moveIndexing, sodiumLayer(), nss());
        tromboneHCD.tell(new HcdController.Submit(new SetupConfig(axisDatumCK.prefix())), self());
        executeMatch(context(), idleMatcher, tromboneHCD, Optional.of(mySender)) {
          case Completed =>
            state(cmdReady, moveIndexed, false, false);
          case Error(message) =>
            log.error("Error: " + message);
        }
      }
    }).
      matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>  DATUM STOP STOP");
        tromboneHCD.tell(new HcdController.Submit(cancelSC), self());
      }).
      matchAny(t -> log.warning("Unknown message received: " + t)).
      build();
  }
}
