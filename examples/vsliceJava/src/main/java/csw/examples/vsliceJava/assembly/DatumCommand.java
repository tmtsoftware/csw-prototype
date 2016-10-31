package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
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
import java.util.concurrent.TimeUnit;

import static csw.examples.vsliceJava.assembly.TromboneStateActor.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.axisDatumCK;
import static csw.examples.vsliceJava.hcd.TromboneHCD.cancelSC;
import static javacsw.services.ccs.JCommandStatus2.Completed;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DatumCommand extends AbstractActor {

  private static final String prefix = "NFIRAOS.cc.lgsTrombone";

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
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
      matchEquals(JSequentialExecution.CommandStart(), t -> {
        if (startState.cmd.head().equals(cmdUninitialized)) {
          sender().tell(new NoLongerValid(new Validation.WrongInternalStateIssue("Assembly state of "
            + startState.cmd + "/" + startState.move + " does not allow datum")), self());
        } else {
          ActorRef mySender = sender();
          sendState(new SetState(cmdItem(cmdBusy), moveItem(moveIndexing), startState.sodiumLayer, startState.nss));
          tromboneHCD.tell(new HcdController.Submit(new SetupConfig(axisDatumCK.prefix())), self());
          Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
          TromboneCommandHandler.executeMatch(context(), TromboneCommandHandler.idleMatcher(), tromboneHCD,  Optional.of(mySender), timeout, status -> {
            if (status == Completed)
              sendState(new SetState(cmdReady, moveIndexed, false, false));
            else if (status instanceof CommandStatus2.Error)
              log.error("Data command match failed with error: " + ((CommandStatus2.Error)status).message());
          });
        }
      }).
      matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>  DATUM STOP STOP");
        tromboneHCD.tell(new HcdController.Submit(cancelSC), self());
      }).
      matchAny(t -> log.warning("Unknown message received: " + t)).
      build());
  }

  private void sendState(SetState setState) {
    stateActor.ifPresent(actorRef -> actorRef.tell(setState, self()));
  }

  public static Props props(SetupConfig sc, ActorRef tromboneHCD, TromboneState startState, Optional<ActorRef> stateActor) {
    return Props.create(new Creator<DatumCommand>() {
      private static final long serialVersionUID = 1L;

      @Override
      public DatumCommand create() throws Exception {
        return new DatumCommand(sc, tromboneHCD, startState, stateActor);
      }
    });
  }

}
