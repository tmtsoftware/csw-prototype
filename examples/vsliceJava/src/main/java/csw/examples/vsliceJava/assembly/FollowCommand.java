package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.util.config.BooleanItem;
import csw.util.config.DoubleItem;
import csw.util.config.Events.EventTime;
import javacsw.services.events.IEventService;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.time.Instant;
import java.util.Optional;

import static csw.examples.vsliceJava.assembly.FollowActor.SetZenithAngle;
import static csw.examples.vsliceJava.assembly.FollowActor.UpdatedEventData;

/**
 * FollowCommand encapsulates the actors that collaborate to implement the Follow command.
 *
 * A FollowCommand actor is created as a side effect when the trombone assembly receives the Follow command. This actor is created
 * and exists after the Follow command and until the Stop command is received.
 *
 * Follow command creates a TromboneControl actor which receives stage position updates.
 * It creates a TromboneEventSubscriber, which subscribes to the zenith angle system events from the TCS, and the focus error system
 * events from the RTC. The TromboneEventSubscriber forwards these updates to the follow actor, which processes them and computes new
 * trombone stage positions as well as other events.
 *
 * The publisher of events is injected into the FollowCommand and injected into the FollowActor. This is done because the publisher is
 * shared in the Assembly by other actors.
 *
 * The follow command acts differently depending on the value of nssInUseIn, which is true of the NFIRAOS Source Simulator is in use or not.
 * See specs, but when in use, the Follow Actor ignores zenith angle updates. When nssInUse is true, the TromboneEventSubscriber always sends
 * the value of 0.0 for the zenith angle no matter what.
 *
 * nssInUse can be updated while operational. This is only used for testing to ensure that the zero angle behavior is working (see tests).
 * During operation, a new FollowCommand is created when the use of the NSS changes. If following, a Stop must be issued, and a new Follow
 * command with nssInUse set accordingly.
 *
 * Note that the actor receive method is parameterized with an optional HCD actor ref. The HCD actor is set initially when
 * the actor is created and may be updated if the actor goes down or up. The actor ref is an [[scala.Option]] so
 * that if the actor ref is set to None, no message will be sent, but the actor can operator normally. FollowCommand forwards this update
 * to the TromboneControl
 *
 * When the FollowCommand actor is killed, all its created actors are killed. The subscriptions to the event service and telemetry service
 * are removed.
 *
 * While "following" the trombone assembly can accept the SetAngle and SetElevation commands, which are forwarded to the Follow actor
 * that executes the algorithms for the trombone assembly.
 *
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class FollowCommand extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final DoubleItem initialElevation;
  private final Optional<ActorRef> eventPublisher;
  private final IEventService eventService;

  // Create the trombone publisher for publishing SystemEvents to AOESW, etc if one is not provided
  private final ActorRef tromboneControl;

  // These are accessed by the tests
  final BooleanItem nssInUseIn;
  final Optional<ActorRef> tromboneHCDIn;


  /**
   * Constructor
   *
   * @param ac the trombone Assembly context contains shared values and functions
   * @param nssInUseIn a BooleanItem, set to true if the NFIRAOS Source Simulator is in use, set to false if not in use
   * @param tromboneHCDIn the actor reference to the trombone HCD as an optional value
   * @param eventPublisher the actor reference to the shared TrombonePublisher actor as an optional value
   * @param eventService  EventService for subscriptions
   */
  private FollowCommand(AssemblyContext ac, DoubleItem initialElevation, BooleanItem nssInUseIn, Optional<ActorRef> tromboneHCDIn,
                        Optional<ActorRef> eventPublisher, IEventService eventService) {
    this.ac = ac;
    this.nssInUseIn = nssInUseIn;
    this.tromboneHCDIn = tromboneHCDIn;
    this.initialElevation = initialElevation;
    this.eventPublisher = eventPublisher;
    this.eventService = eventService;

    tromboneControl = context().actorOf(TromboneControl.props(ac, tromboneHCDIn), "trombonecontrol");
    ActorRef initialFollowActor = createFollower(initialElevation, nssInUseIn, tromboneControl, eventPublisher, eventPublisher);
    ActorRef initialEventSubscriber = createEventSubscriber(nssInUseIn, initialFollowActor, eventService);

    receive(followReceive(nssInUseIn, initialFollowActor, initialEventSubscriber, tromboneHCDIn));
  }

  private PartialFunction<Object, BoxedUnit> followReceive(BooleanItem nssInUse, ActorRef followActor,
                                                           ActorRef eventSubscriber, Optional<ActorRef> tromboneHCD) {
    //noinspection CodeBlock2Expr
    return ReceiveBuilder.
      match(StopFollowing.class, t -> {
        log.info("Receive stop following in Follow Command");
        // Send this so that unsubscriptions happen, need to check if needed
        context().stop(eventSubscriber);
        context().stop(followActor);
        context().stop(self());
      }).
      match(UpdateNssInUse.class, t -> {
        if (t.nssInUse != nssInUse) {
          // First stop the currents so we can create new ones
          context().stop(eventSubscriber);
          context().stop(followActor);
          // Note that follower has the option of a different publisher for events and telemetry, but this is primarily useful for testing
          ActorRef newFollowActor = createFollower(initialElevation, t.nssInUse, tromboneControl, eventPublisher, eventPublisher);
          ActorRef newEventSubscriber = createEventSubscriber(t.nssInUse, newFollowActor, eventService);
          // Set a new receive method with updated actor values, prefer this over vars or globals
          context().become(followReceive(t.nssInUse, newFollowActor, newEventSubscriber, tromboneHCD));
        }
      }).
      match(SetZenithAngle.class, t -> {
        log.debug("Got angle: " + t.zenithAngle);
        followActor.tell(t, sender());

      }).match(TromboneAssembly.UpdateTromboneHCD.class, upd -> {
          // Note that this is an option so it can be None
          // Set a new receive method with updated actor values and new HCD, prefer this over vars or globals
        context().become(followReceive(nssInUse, followActor, eventSubscriber, upd.tromboneHCD));
        // Also update the trombone control with the new HCD reference
        tromboneControl.tell(new TromboneAssembly.UpdateTromboneHCD(upd.tromboneHCD), self());
      }).
      match(UpdateZAandFE.class, t -> {
        followActor.tell(new UpdatedEventData(t.zenithAngle, t.focusError, new EventTime(Instant.now())), self());
      }).
      matchAny(t -> log.warning("Unexpected message received in TromboneAssembly:FollowCommand: " + t)).
      build();
  }


  private ActorRef createFollower(DoubleItem initialElevation, BooleanItem nssInUse, ActorRef tromboneControl, Optional<ActorRef> eventPublisher, Optional<ActorRef> telemetryPublisher) {
    return context().actorOf(FollowActor.props(ac, initialElevation, nssInUse, Optional.of(tromboneControl), eventPublisher, eventPublisher), "follower");
  }

  private ActorRef createEventSubscriber(BooleanItem nssItem, ActorRef followActor, IEventService eventService) {
    return context().actorOf(TromboneEventSubscriber.props(ac, nssItem, Optional.of(followActor), eventService), "eventsubscriber");
  }

  // --- static defs ---

  public static Props props(AssemblyContext ac, DoubleItem initialElevation, BooleanItem nssInUseIn, Optional<ActorRef> tromboneHCDIn,
                            Optional<ActorRef> eventPublisher, IEventService eventService) {
    return Props.create(new Creator<FollowCommand>() {
      private static final long serialVersionUID = 1L;

      @Override
      public FollowCommand create() throws Exception {
        return new FollowCommand(ac, initialElevation, nssInUseIn, tromboneHCDIn, eventPublisher, eventService);
      }
    });
  }


  interface FollowCommandMessages {}

  public static class StopFollowing implements FollowCommandMessages {}

  @SuppressWarnings("WeakerAccess")
  public static class UpdateNssInUse implements FollowCommandMessages {
    public final BooleanItem nssInUse;

    public UpdateNssInUse(BooleanItem nssInUse) {
      this.nssInUse = nssInUse;
    }
  }

  /**
   * This is an engineering and test method that is used to trigger the same kind of update as a zenith angle and focus error
   * events from external to the Assembly
   */
  @SuppressWarnings("WeakerAccess")
  public static class UpdateZAandFE {
    public final DoubleItem zenithAngle;
    public final DoubleItem focusError;

    /**
     * Constructor
     * @param zenithAngle a zenith angle in degrees as a [[csw.util.config.DoubleItem]]
     * @param focusError a focus error value in
     */
    public UpdateZAandFE(DoubleItem zenithAngle, DoubleItem focusError) {
      this.zenithAngle = zenithAngle;
      this.focusError = focusError;
    }
  }
}

