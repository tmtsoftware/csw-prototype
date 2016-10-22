package csw.examples.vsliceJava.assembly

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.ts.AbstractTimeServiceScheduler;
import csw.util.config.JavaHelpers;
import csw.util.config.StateVariable.CurrentState;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import javacsw.util.config.JPublisherActor;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import csw.examples.vsliceJava.assembly.TrombonePublisher.*;

import java.util.Optional;

import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.GetAxisStats;
import static javacsw.services.ts.JTimeService.localTimeNow;

/**
 * DiagPublisher provides diagnostic telemetry in the form of two events. DiagPublisher operaties in the 'OperationsState' or 'DiagnosticState'.
 * <p>
 * DiagPublisher listens in on axis state updates from the HCD and publishes them as a StatusEvent through the assembly's event publisher.
 * In OperationsState, it publishes every 5'th axis state update (set with val operationsSkipCount.
 * In DiagnosticState, it publishes every other axis update (more frequent in diagnostic state).
 * <p>
 * context.become is used to implement a state machine with two states operationsReceive and diagnosticReceive
 * <p>
 * In DiagnosticState, it also publishes an axis statistics event every second. Every one second (diagnosticAxisStatsPeriod), it
 * sends the GetAxisStats message to the HCD. When the data arrives, it is sent to the event publisher.
 * <p>
 * This actor demonstrates a few techniques. First, it has no variables. Each state in the actor is represented by its own
 * receive method. Each method has parameters that can be called from within the function with updated values eliminating the need
 * for variables.
 * <p>
 * This shows how to filter events from the CurrentState stream from the HCD.
 * <p>
 * This shows how to use the TimeService to send periodic messages and how to periodically call another actor and process its
 * response.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DiagPublisher extends AbstractTimeServiceScheduler {

  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final ActorRef currentStateReceiver;
  private final Optional<ActorRef> tromboneHCDIn;
  private final Optional<ActorRef> eventPublisher;

  private DiagPublisher(ActorRef currentStateReceiver, Optional<ActorRef> tromboneHCDIn, Optional<ActorRef> eventPublisher) {
//      getContext().setReceiveTimeout(timeout);

    this.currentStateReceiver = currentStateReceiver;
    this.tromboneHCDIn = tromboneHCDIn;
    this.eventPublisher = eventPublisher;

    currentStateReceiver.tell(JPublisherActor.Subscribe, self());
    // It would be nice if this message was in a more general location than HcdController or

    // Start in operations mode - 0 is initial stateMessageCounter value
    getContext().become(operationsReceive(currentStateReceiver, 0, tromboneHCDIn));

//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());
  }

  /**
   * The receive method in operations state.
   * <p>
   * In operations state every 5th AxisUpdate message from the HCD is published as a status event. It sends an AxisStateUpdate message
   * to the event publisher
   *
   * @param currentStateReceive the source for CurrentState messages
   * @param stateMessageCounter the number of messages received by the diag publisher
   * @param tromboneHCD         the trombone HCD ActorRef as an Option
   * @return Receive partial function
   */
  PartialFunction<Object, BoxedUnit> operationsReceive(ActorRef currentStateReceive, int stateMessageCounter, Optional<ActorRef> tromboneHCD) {
    return ReceiveBuilder.
      match(CurrentState.class, cs -> {
        if (cs.configKey().equals(TromboneHCD.axisStateCK)) {
          if (stateMessageCounter % operationsSkipCount == 0) publishStateUpdate(cs);
          context().become(operationsReceive(currentStateReceive, stateMessageCounter + 1, tromboneHCD));
        } else if (!cs.configKey().equals(TromboneHCD.axisStatsCK)) {
          log.warning("Unknown message received: " + cs);
        }
      }).
      match(TimeForAxisStats.class, t -> {
        // Do nothing, here so it doesn't make an error
      }).
      match(OperationsState.class, t -> {
        // Already in operaitons mode
      }).
      match(DiagnosticState.class, t -> {
        // If the DiagnosticMode message is received, begin collecting axis stats messages based on a timer and query to HCD
        // The cancelToken allows turning off the timer when
        Cancellable cancelToken = scheduleOnce(localTimeNow().plusSeconds(diagnosticAxisStatsPeriod), self(), new TimeForAxisStats(diagnosticAxisStatsPeriod));
        context().become(diagnosticReceive(currentStateReceive, stateMessageCounter, tromboneHCD, cancelToken));
      }).
      match(UpdateTromboneHCD.class, t -> {
        context().become(operationsReceive(currentStateReceiver, stateMessageCounter, t.tromboneHCDUpdate));
      }).
      matchAny(t -> log.warning("DiagPublisher:operationsReceive received an unexpected message: " + t)).
      build();
  }

  /**
   * The receive method in diagnostic state
   *
   * @param currentStateReceive the source for CurrentState messages
   * @param stateMessageCounter the number of messages received by the diag publisher
   * @param tromboneHCD         the trombone HCD ActorRef as an Option
   * @param cancelToken         a token that allows the current timer to be cancelled
   * @return Receive partial function
   */
  PartialFunction<Object, BoxedUnit> diagnosticReceive(ActorRef currentStateReceive, int stateMessageCounter,
                                                       Optional<ActorRef> tromboneHCD, Cancellable cancelToken) {
    return ReceiveBuilder.
      match(CurrentState.class, cs -> {
        if (cs.configKey().equals(TromboneHCD.axisStateCK)) {
          if (stateMessageCounter % operationsSkipCount == 0) publishStateUpdate(cs);
          context().become(diagnosticReceive(currentStateReceive, stateMessageCounter + 1, tromboneHCD, cancelToken));
        } else if (!cs.configKey().equals(TromboneHCD.axisStatsCK)) {
          // Here when a CurrentState is received with the axisStats configKey, the axis statistics are published as an event
          publishStatsUpdate(cs);
        }
      }).
      match(TimeForAxisStats.class, t -> {
//        case TimeForAxisStats(periodInSeconds) =>
        // Here, every period, an Axis statistics is requested, which is then pubilshed for diagnostics when the response arrives
        // This shows how to periodically query the HCD
        tromboneHCD.ifPresent(actorRef -> actorRef.tell(GetAxisStats, self()));
        Cancellable canceltoken = scheduleOnce(localTimeNow().plusSeconds(t.periodInSeconds), self(), new TimeForAxisStats(t.periodInSeconds));
        context().become(diagnosticReceive(currentStateReceive, stateMessageCounter, tromboneHCD, canceltoken));
      }).
      match(DiagnosticState.class, t -> {
        // Do nothing, already in this mode
      }).
      match(OperationsState.class, t -> {
        // Switch to Operations State
        cancelToken.cancel();
        context().become(operationsReceive(currentStateReceive, stateMessageCounter, tromboneHCD));
      }).
      match(UpdateTromboneHCD.class, t -> {
        // The actor ref of the trombone HCD has changed
        context().become(diagnosticReceive(currentStateReceiver, stateMessageCounter, t.tromboneHCDUpdate, cancelToken));
      }).
      matchAny(t -> log.warning("DiagPublisher:diagnosticReceive received an unexpected message: " + t)).
      build();
  }


  private void publishStateUpdate(CurrentState cs) {
    eventPublisher.ifPresent(actorRef ->
      actorRef.tell(new AxisStateUpdate(
          JavaHelpers.jvalue(cs, axisNameKey),
          JavaHelpers.jvalue(cs, positionKey),
          JavaHelpers.jvalue(cs, stateKey),
          JavaHelpers.jvalue(cs, inLowLimitKey),
          JavaHelpers.jvalue(cs, inHighLimitKey),
          JavaHelpers.jvalue(cs, inHomeKey)),
        self()));
  }

  private void publishStatsUpdate(CurrentState cs) {
    eventPublisher.ifPresent(actorRef ->
      actorRef.tell(new AxisStatsUpdate(
          JavaHelpers.jvalue(cs, axisNameKey),
          JavaHelpers.jvalue(cs, datumCountKey),
          JavaHelpers.jvalue(cs, moveCountKey),
          JavaHelpers.jvalue(cs, homeCountKey),
          JavaHelpers.jvalue(cs, limitCountKey),
          JavaHelpers.jvalue(cs, successCountKey),
          JavaHelpers.jvalue(cs, failureCountKey),
          JavaHelpers.jvalue(cs, cancelCountKey)),
        self()));
  }


  // --- static defs ---

  private static final int diagnosticSkipCount = 2;
  private static final int operationsSkipCount = 5;

  // Following are in units of seconds - could be in a configuration file
  private static final int diagnosticAxisStatsPeriod = 1;

  /**
   * Base class for actor messages received
   */
  public interface DiagPublisherMessages {
  }

  public static class TimeForAxisStats implements DiagPublisherMessages {
    public final int periodInSeconds;

    public TimeForAxisStats(int periodInSeconds) {
      this.periodInSeconds = periodInSeconds;
    }
  }

  public static class DiagnosticState implements DiagPublisherMessages {
  }

  public static class OperationsState implements DiagPublisherMessages {
  }

  /**
   * Returns the props to use to create the actor.
   *
   * @param currentStateReceiver a source for CurrentState messages. This can be the actorRef of the HCD itself, or the actorRef of
   *                             a CurrentStateReceiver
   * @param tromboneHCDIn        actorRef of the tromboneHCD as a [[scala.Option]]
   * @param eventPublisher       actorRef of an instance of the TrombonePublisher as [[scala.Option]]
   */
  public static Props props(ActorRef currentStateReceiver, Optional<ActorRef> tromboneHCDIn, Optional<ActorRef> eventPublisher) {
    return Props.create(new Creator<DiagPublisher>() {
      private static final long serialVersionUID = 1L;

      @Override
      public DiagPublisher create() throws Exception {
        return new DiagPublisher(currentStateReceiver, tromboneHCDIn, eventPublisher);
      }
    });
  }
}


