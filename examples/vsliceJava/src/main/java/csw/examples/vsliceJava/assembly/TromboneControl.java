package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.util.config.DoubleItem;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Optional;

import static csw.services.ccs.HcdController.Submit;
import static javacsw.util.config.JItems.jvalue;

/**
 * An actor dedicated to converting stage position values to encoder units and writing in a oneway fashion to
 * the trombone HCD.
 * <p>
 * Other actors, primarily the FollowActor write stage positions with units of millimeters. This actor uses the
 * function in algorithms to convert this to encoder units. It then uses the Submit command of CCS to send the
 * SetupConfig to the trombone HCD.
 * <p>
 * Note that the actor receive method is parameterized with an optional HCD actor ref. It is set initially when
 * the actor is created and may be updated if the actor goes down or up. The actor ref is an [[scala.Option]] so
 * that if the actor ref is set to None, no message will be sent, but the actor can operator normally.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class TromboneControl extends AbstractActor {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;

  /**
   * Constructor
   *
   * @param ac            the trombone AssemblyContext contains important shared values and useful function
   * @param tromboneHCDIn the actor reference to the trombone HCD as a [[scala.Option]]
   */
  private TromboneControl(AssemblyContext ac, Optional<ActorRef> tromboneHCDIn) {
    this.ac = ac;
    log.info("TromboneIn: ========> " + tromboneHCDIn);

    // Initial receive - start with initial values
    receive(controlReceive(tromboneHCDIn));
  }

  private PartialFunction<Object, BoxedUnit> controlReceive(Optional<ActorRef> tromboneHCD) {
    return ReceiveBuilder.
      match(GoToStagePosition.class, t -> {
        DoubleItem newPosition = t.stagePosition;
        // It should be correct, but check
        assert (newPosition.units() == ac.stagePositionUnits);

        // Convert to encoder units
        int encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, jvalue(newPosition));

        // Final check before sending off to hardware
        log.info("epos: " + encoderPosition + ", minLimit: " + ac.controlConfig.minEncoderLimit + ", maxEnc: " + ac.controlConfig.maxEncoderLimit);
        assert (encoderPosition > ac.controlConfig.minEncoderLimit && encoderPosition < ac.controlConfig.maxEncoderLimit);

        log.debug("Setting trombone axis to stage position: " + jvalue(newPosition) + " and encoder: " + encoderPosition);

        // Send command to HCD here
        tromboneHCD.ifPresent(actorRef -> actorRef.tell(new Submit(TromboneHCD.positionSC(encoderPosition)), self()));
      }).
      match(TromboneAssembly.UpdateTromboneHCD.class, t -> context().become(controlReceive(t.tromboneHCD))).
      matchAny(t -> log.warning("Unexpected message received in TromboneControl:controlReceive: " + t)).
      build();
  }

  // --- static defs ---

  // Props for creating the TromboneControl actor
  public static Props props(AssemblyContext ac, Optional<ActorRef> tromboneHCDIn) {
    return Props.create(new Creator<TromboneControl>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneControl create() throws Exception {
        return new TromboneControl(ac, tromboneHCDIn);
      }
    });
  }

  // Used to send a position that requries transformaton from
  static class GoToStagePosition {
    final DoubleItem stagePosition;

    GoToStagePosition(DoubleItem stagePosition) {
      this.stagePosition = stagePosition;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GoToStagePosition that = (GoToStagePosition) o;

      return stagePosition != null ? stagePosition.equals(that.stagePosition) : that.stagePosition == null;
    }

    @Override
    public int hashCode() {
      return stagePosition != null ? stagePosition.hashCode() : 0;
    }

    @Override
    public String toString() {
      return "GoToStagePosition{" +
        "stagePosition=" + stagePosition +
        '}';
    }
  }
}

