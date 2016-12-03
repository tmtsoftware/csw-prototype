package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import csw.services.ccs.CommandStatus.Error;
import csw.services.ccs.HcdController;
import csw.services.ccs.Validation;
import javacsw.services.ccs.JSequentialExecutor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static csw.examples.vsliceJava.assembly.TromboneStateActor.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.axisDatumCK;
import static csw.examples.vsliceJava.hcd.TromboneHCD.cancelSC;
import static csw.services.ccs.CommandStatus.NoLongerValid;
import static csw.util.config.Configurations.SetupConfig;
import static javacsw.services.ccs.JCommandStatus.Completed;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DatumCommand extends AbstractActor {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  private final Optional<ActorRef> stateActor;

  private DatumCommand(SetupConfig sc, ActorRef tromboneHCD, TromboneState startState, Optional<ActorRef> stateActor) {
    this.stateActor = stateActor;

    // Not using stateReceive since no state updates are needed here only writes
    receive(ReceiveBuilder.
      matchEquals(JSequentialExecutor.CommandStart(), t -> {
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
            else if (status instanceof Error)
              log.error("Data command match failed with error: " + ((Error)status).message());
          });
        }
      }).
      matchEquals(JSequentialExecutor.StopCurrentCommand(), t -> {
        log.info(">> DATUM STOPPED");
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
