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
import csw.services.ccs.StateMatchers;
import csw.services.ccs.Validation;
import csw.util.config.Configurations;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.DoubleItem;
import csw.util.config.JavaHelpers;
import javacsw.services.ccs.JSequentialExecution;
import csw.examples.vsliceJava.assembly.TromboneStateActor.TromboneState;
import csw.examples.vsliceJava.assembly.TromboneStateActor.SetState;
import csw.services.ccs.CommandStatus2.NoLongerValid;
import csw.services.ccs.Validation.WrongInternalStateIssue;
import csw.services.ccs.StateMatchers.DemandMatcher;
import javacsw.util.config.JUnitsOfMeasure;

import java.util.Optional;

import static csw.examples.vsliceJava.assembly.TromboneStateActor.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static javacsw.services.ccs.JCommandStatus2.Completed;
import static javacsw.util.config.JConfigDSL.sc;
import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JUnitsOfMeasure.encoder;

/**
 * This actor implements the setElevation command.
 *
 * The setElevation command receives an elevation value. In this implementation, the elevation is used as a range distance
 * that is then converted to a state position and sent to the HCD.
 *
 * This command is similar to the move command and would use the move command except for the fact that at the end of the
 * command, the sodium layer state must be set to true, which is not the case with the mvoe command.  There is probably
 * a way to refactor this to reuse the move command.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SetElevationCommand extends AbstractActor {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  private final AssemblyContext ac;
  private final SetupConfig sc;
  private final ActorRef tromboneHCD;
  private final TromboneState startState;
  private final Optional<ActorRef> stateActor;

  private SetElevationCommand(AssemblyContext ac, SetupConfig sc, ActorRef tromboneHCD, TromboneState startState, Optional<ActorRef> stateActor) {
    this.ac = ac;
    this.sc = sc;
    this.tromboneHCD = tromboneHCD;
    this.startState = startState;
    this.stateActor = stateActor;

    // Not using stateReceive since no state updates are needed here only writes
    receive(ReceiveBuilder.
      matchEquals(JSequentialExecution.CommandStart(), t -> {
        if (cmd(startState) == cmdUninitialized || (move(startState) != moveIndexed && move(startState) != moveMoving)) {
          sender().tell(new NoLongerValid(new WrongInternalStateIssue(
            "Assembly state of " + cmd(startState) + "/" + move(startState) + " does not allow setElevation")), self());
        } else {
          ActorRef mySender = sender();
          // Note that units have already been verified here
          DoubleItem elevationItem = JavaHelpers.jvalue(sc, ac.naElevationKey);

          // Let the elevation be the range distance
          // Convert range distance to encoder units from mm
          double stagePosition = Algorithms.rangeDistanceToStagePosition(jvalue(elevationItem));
          int encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition);

          log.info("Using elevation as rangeDistance: " + jvalue(elevationItem) + " to get stagePosition: " + stagePosition " + to encoder: " + encoderPosition);

          DemandMatcher stateMatcher = TromboneCommandHandler.posMatcher(encoderPosition);
          // Position key is encoder units
          SetupConfig scOut = jadd(sc(axisMoveCK.prefix(), jset(positionKey, encoderPosition).withUnits(encoder)));
          sendState(new SetState(cmdItem(cmdBusy), moveItem(moveMoving), startState.sodiumLayer, startState.nss));
          tromboneHCD.tell(new HcdController.Submit(scOut), self());

          executeMatch(context(), stateMatcher, tromboneHCD, Optional.of(mySender)) {
            case Completed =>
              // NOTE ---> This is the place where sodium layer state gets set to TRUE
              sendState(new SetState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), startState.nss));
            case Error(message) =>
              log.error("setElevation command match failed with message: " + message);
          }
        }
      }).
      matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
        log.info("SetElevation command -- STOP");
        tromboneHCD.tell(new HcdController.Submit(cancelSC), self());
      }).
      matchAny(t -> log.warning("Unknown message received: " + t)).
      build());
  }

  private void sendState(SetState setState) {
    stateActor.ifPresent(actorRef -> actorRef.tell(setState, self()));
  }

  public static Props props(AssemblyContext ac, SetupConfig sc, ActorRef tromboneHCD, TromboneState startState, Optional<ActorRef> stateActor) {
    return Props.create(new Creator<SetElevationCommand>() {
      private static final long serialVersionUID = 1L;

      @Override
      public SetElevationCommand create() throws Exception {
        return new SetElevationCommand(ac, sc, tromboneHCD, startState, stateActor);
      }
    });
  }

}
