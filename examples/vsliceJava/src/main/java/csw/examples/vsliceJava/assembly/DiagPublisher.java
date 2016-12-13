package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.loc.LocationService;
import csw.services.ts.AbstractTimeServiceScheduler;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.ccs.JHcdController;
import javacsw.services.pkg.ILocationSubscriberClient;
import javacsw.util.config.JPublisherActor;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Objects;
import java.util.Optional;

import static csw.examples.vsliceJava.assembly.TrombonePublisher.AxisStateUpdate;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.AxisStatsUpdate;
import static csw.examples.vsliceJava.hcd.TromboneHCD.TromboneEngineering.GetAxisStats;
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.services.ts.TimeService.localTimeNow;
import static javacsw.util.config.JItems.jitem;
import static csw.services.loc.LocationService.Location;

/**
 * DiagPublisher provides diagnostic telemetry in the form of two events. DiagPublisher operaties in the 'OperationsState' or 'DiagnosticState'.
 *
 * DiagPublisher listens in on axis state updates from the HCD and publishes them as a StatusEvent through the assembly's event publisher.
 * In OperationsState, it publishes every 5'th axis state update (set with val operationsSkipCount.
 * In DiagnosticState, it publishes every other axis update (more frequent in diagnostic state).
 *
 * context.become is used to implement a state machine with two states operationsReceive and diagnosticReceive
 *
 * In DiagnosticState, it also publishes an axis statistics event every second. Every one second (diagnosticAxisStatsPeriod), it
 * sends the GetAxisStats message to the HCD. When the data arrives, it is sent to the event publisher.
 *
 * This actor demonstrates a few techniques. First, it has no variables. Each state in the actor is represented by its own
 * receive method. Each method has parameters that can be called from within the function with updated values eliminating the need
 * for variables.
 *
 * This shows how to filter events from the CurrentState stream from the HCD.
 *
 * This shows how to use the TimeService to send periodic messages and how to periodically call another actor and process its
 * response.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public class DiagPublisher extends AbstractTimeServiceScheduler implements ILocationSubscriberClient {

  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

//  private final ActorRef currentStateReceiver;
  private final Optional<ActorRef> eventPublisher;
  private final String hcdName;

  /**
   * Constructor
   *
   * @param assemblyContext      the assembly context provides overall assembly information and convenience functions
   * @param tromboneHCDIn        initial actorRef of the tromboneHCD as a [[scala.Option]]
   * @param eventPublisher     initial actorRef of an instance of the TrombonePublisher as [[scala.Option]]
   */
  private DiagPublisher(AssemblyContext assemblyContext, Optional<ActorRef> tromboneHCDIn, Optional<ActorRef> eventPublisher) {
//    this.currentStateReceiver = currentStateReceiver;
    this.eventPublisher = eventPublisher;

    subscribeToLocationUpdates();

    tromboneHCDIn.ifPresent(actorRef -> actorRef.tell(JPublisherActor.Subscribe, self()));
    // It would be nice if this message was in a more general location than HcdController or

    // This works because we only have one HCD
    this.hcdName = assemblyContext.info.getConnections().get(0).name();

    // Start in operations mode - 0 is initial stateMessageCounter value
    receive(operationsReceive(/*currentStateReceiver,*/ 0, tromboneHCDIn));
  }

  /**
   * The receive method in operations state.
   * <p>
   * In operations state every 5th AxisUpdate message from the HCD is published as a status event. It sends an AxisStateUpdate message
   * to the event publisher
   *
   * @param stateMessageCounter the number of messages received by the diag publisher
   * @param tromboneHCD         the trombone HCD ActorRef as an Option
   * @return Receive partial function
   */
  private PartialFunction<Object, BoxedUnit> operationsReceive(int stateMessageCounter, Optional<ActorRef> tromboneHCD) {
    //noinspection CodeBlock2Expr
    return ReceiveBuilder.
      match(CurrentState.class, cs -> {
        if (cs.configKey().equals(TromboneHCD.axisStateCK)) {
          if (stateMessageCounter % operationsSkipCount == 0) publishStateUpdate(cs);
          context().become(operationsReceive(stateMessageCounter + 1, tromboneHCD));
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
        context().become(diagnosticReceive(stateMessageCounter, tromboneHCD, cancelToken));
      }).
      match(TromboneAssembly.UpdateTromboneHCD.class, t -> {
        context().become(operationsReceive(stateMessageCounter, t.tromboneHCD));
      }).
      match(Location.class, location -> {

        if (location instanceof LocationService.ResolvedAkkaLocation) {
          if (Objects.equals(location.connection().name(), hcdName)) {
            LocationService.ResolvedAkkaLocation rloc = (LocationService.ResolvedAkkaLocation) location;
            log.info("operationsReceive updated actorRef: " + rloc.getActorRef());
            Optional<ActorRef> newHcdActorRef = rloc.getActorRef();
            newHcdActorRef.ifPresent(actorRef -> actorRef.tell(JHcdController.Subscribe, self()));
            context().become(operationsReceive(stateMessageCounter, newHcdActorRef));
          }

        } else if (location instanceof LocationService.Unresolved) {
          if (Objects.equals(location.connection().name(), hcdName)) {
            log.info("operationsReceive got unresolve for trombone HCD");
            context().become(operationsReceive(stateMessageCounter, Optional.empty()));
          }
        } else if (location instanceof LocationService.UnTrackedLocation) {
          if (Objects.equals(location.connection().name(), hcdName)) {
            log.info("operationsReceive got untrack for trombone HCD");
            context().become(operationsReceive(stateMessageCounter, Optional.empty()));
          }
        }
      }).
      matchAny(t -> log.warning("DiagPublisher:operationsReceive received an unexpected message: " + t)).
      build();
  }

  /**
   * The receive method in diagnostic state
   *
   * @param stateMessageCounter the number of messages received by the diag publisher
   * @param tromboneHCD         the trombone HCD ActorRef as an Option
   * @param cancelToken         a token that allows the current timer to be cancelled
   * @return Receive partial function
   */
  private PartialFunction<Object, BoxedUnit> diagnosticReceive(int stateMessageCounter,
                                                               Optional<ActorRef> tromboneHCD, Cancellable cancelToken) {
    return ReceiveBuilder.
      match(CurrentState.class, cs -> {
        if (cs.configKey().equals(TromboneHCD.axisStateCK)) {
          if (stateMessageCounter % diagnosticSkipCount == 0) publishStateUpdate(cs);
          context().become(diagnosticReceive(stateMessageCounter + 1, tromboneHCD, cancelToken));
        } else if (cs.configKey().equals(TromboneHCD.axisStatsCK)) {
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
        context().become(diagnosticReceive(stateMessageCounter, tromboneHCD, canceltoken));
      }).
      match(DiagnosticState.class, t -> {
        // Do nothing, already in this mode
      }).
      match(OperationsState.class, t -> {
        // Switch to Operations State
        cancelToken.cancel();
        context().become(operationsReceive(stateMessageCounter, tromboneHCD));
      }).
      match(TromboneAssembly.UpdateTromboneHCD.class, t -> {
        // The actor ref of the trombone HCD has changed
        context().become(diagnosticReceive(stateMessageCounter, t.tromboneHCD, cancelToken));
      }).
      match(Location.class, location -> {

        if (location instanceof LocationService.ResolvedAkkaLocation) {
          if (Objects.equals(location.connection().name(), hcdName)) {
            LocationService.ResolvedAkkaLocation rloc = (LocationService.ResolvedAkkaLocation) location;
            log.info("diagnosticReceive updated actorRef: " + rloc.getActorRef());
            Optional<ActorRef> newHcdActorRef = rloc.getActorRef();
            newHcdActorRef.ifPresent(actorRef -> actorRef.tell(JHcdController.Subscribe, self()));
            context().become(diagnosticReceive(stateMessageCounter, newHcdActorRef, cancelToken));
          }

        } else if (location instanceof LocationService.Unresolved) {
          if (Objects.equals(location.connection().name(), hcdName)) {
            log.info("diagnosticReceive got unresolve for trombone HCD");
            context().become(diagnosticReceive(stateMessageCounter, Optional.empty(), cancelToken));
          }

        } else if (location instanceof LocationService.UnTrackedLocation) {
          if (Objects.equals(location.connection().name(), hcdName)) {
            log.info("diagnosticReceive got untrack for trombone HCD");
            context().become(diagnosticReceive(stateMessageCounter, Optional.empty(), cancelToken));
          }
        }
      }).
      matchAny(t -> log.warning("DiagPublisher:diagnosticReceive received an unexpected message: " + t)).
      build();
  }


  private void publishStateUpdate(CurrentState cs) {
    log.debug("publish state: " + cs);
    eventPublisher.ifPresent(actorRef ->
      actorRef.tell(new AxisStateUpdate(
          jitem(cs, axisNameKey),
          jitem(cs, positionKey),
          jitem(cs, stateKey),
          jitem(cs, inLowLimitKey),
          jitem(cs, inHighLimitKey),
          jitem(cs, inHomeKey)),
        self()));
  }

  private void publishStatsUpdate(CurrentState cs) {
    log.debug("publish stats");
    eventPublisher.ifPresent(actorRef ->
      actorRef.tell(new AxisStatsUpdate(
          jitem(cs, axisNameKey),
          jitem(cs, datumCountKey),
          jitem(cs, moveCountKey),
          jitem(cs, homeCountKey),
          jitem(cs, limitCountKey),
          jitem(cs, successCountKey),
          jitem(cs, failureCountKey),
          jitem(cs, cancelCountKey)),
        self()));
  }


  // --- static defs ---

  private static final int diagnosticSkipCount = 2;
  private static final int operationsSkipCount = 5;

  // Following are in units of seconds - could be in a configuration file
  private static final int diagnosticAxisStatsPeriod = 1;

  /**
   * Internal messages used by diag publisher
   */
  @SuppressWarnings("WeakerAccess")
  public interface DiagPublisherMessages {
  }

  @SuppressWarnings("WeakerAccess")
  public static class TimeForAxisStats implements DiagPublisherMessages {
    public final int periodInSeconds;

    public TimeForAxisStats(int periodInSeconds) {
      this.periodInSeconds = periodInSeconds;
    }
  }

  @SuppressWarnings("WeakerAccess")
  public static class DiagnosticState implements DiagPublisherMessages {
  }

  @SuppressWarnings("WeakerAccess")
  public static class OperationsState implements DiagPublisherMessages {
  }

  /**
   * Used to create the actor.
   *
   * @param assemblyContext      the assembly context provides overall assembly information and convenience functions
   * @param tromboneHCDIn        initial actorRef of the tromboneHCD as a [[scala.Option]]
   * @param eventPublisher     initial actorRef of an instance of the TrombonePublisher as [[scala.Option]]
   */
  public static Props props(AssemblyContext assemblyContext, Optional<ActorRef> tromboneHCDIn, Optional<ActorRef> eventPublisher) {
    return Props.create(new Creator<DiagPublisher>() {
      private static final long serialVersionUID = 1L;

      @Override
      public DiagPublisher create() throws Exception {
        return new DiagPublisher(assemblyContext, tromboneHCDIn, eventPublisher);
      }
    });
  }
}


