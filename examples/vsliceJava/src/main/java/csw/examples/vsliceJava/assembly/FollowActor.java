package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import csw.util.config.BooleanItem;
import csw.util.config.DoubleItem;
import csw.util.config.Events.*;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.time.Instant;
import java.util.Optional;

import static csw.examples.vsliceJava.assembly.Algorithms.*;
import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JItems.jvalue;
import static csw.examples.vsliceJava.assembly.AssemblyContext.TromboneCalculationConfig;
import static csw.examples.vsliceJava.assembly.AssemblyContext.TromboneControlConfig;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.AOESWUpdate;
import static csw.examples.vsliceJava.assembly.TrombonePublisher.EngrUpdate;

/**
 * FollowActor uses events from TCS and RTC to calculate the position of the trombone assembly when in follow mode, which is set
 * using the follow command. While following, the follow actor calculates the position of the trombone axis and sends it to the
 * trombone HCD represented by the tromboneControl actor. The position is sent as a stage position in stage position units.
 *
 * FollowActor uses the ZenithAngle system event from the TCS and Focus Error system event from the RTC to make its
 * calculations. It receives this data in the form of UpdatedEventData messages from the TromboneEventSubscriber actor. This connection
 * is made in the FollowCommandActor. This is done to allow testing of the actors and functionality separately.
 *
 * FollowActor receives the calculation and control configurations and a flag BooleanItem called inNSSMode.  When inNSSMode is true,
 * the NFIRAOS Source Simulator is in use. In this mode, the FollowActor ignores the TCS zenith angle event data and provides 0.0 no
 * matter what the focus error.
 *
 * FollowActor also calculates the eng event and sodiumLayer telemetry events, which are sent while following. The sodiumLayer event
 * is only published when not in NSS mode according to my reading of the spec. All events are sent as messages to the TrombonePublisher
 * actor, which handles the connection to the event and telemetry services.  There is an aoPublisher and engPublisher in the constructor
 * of the actor to allow easier testing the publishing of the two types of events, but during operation both are set to the same
 * TrombonePublisher actor reference.
 *
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class FollowActor extends AbstractActor {

  // --- non static defs ---

  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final BooleanItem inNSSMode;
  private final Optional<ActorRef> tromboneControl;
  private final Optional<ActorRef> aoPublisher;
  private final Optional<ActorRef> engPublisher;

  private final TromboneCalculationConfig calculationConfig;
  public final DoubleItem initialElevation;

  /**
   * Constructor
   *
   * @param ac AssemblyContext provides the configurations and other values
   * @param inNSSMode a BooleanItem set to true if the NFIRAOS Source Simulator is currently in use
   * @param tromboneControl an actorRef as [[scala.Option]] of the actor that writes the position to the trombone HCD
   * @param aoPublisher an actorRef as [[scala.Option]] of the actor that publishes the sodiumLayer event
   * @param engPublisher an actorRef as [[scala.Option]] of the actor that publishes the eng telemetry event
   */
  private FollowActor(AssemblyContext ac, DoubleItem initialElevation, BooleanItem inNSSMode, Optional<ActorRef> tromboneControl,
                     Optional<ActorRef> aoPublisher, Optional<ActorRef> engPublisher) {
    this.ac = ac;
    this.initialElevation = initialElevation;
    this.inNSSMode = inNSSMode;
    this.tromboneControl = tromboneControl;
    this.aoPublisher = aoPublisher;
    this.engPublisher = engPublisher;

    calculationConfig = ac.calculationConfig;

    // In this implementation, these vars are needed to support the setElevation and setAngle commands which require an update
    DoubleItem initialFocusError = jset(ac.focusErrorKey, 0.0).withUnits(ac.focusErrorUnits);
    DoubleItem initialZenithAngle = jset(ac.zenithAngleKey, 0.0).withUnits(ac.zenithAngleUnits);

//    DoubleItem nSSModeZenithAngle = jset(ac.zenithAngleKey, 0.0).withUnits(ac.zenithAngleUnits);

    // Initial receive - start with initial values
    receive(followingReceive(initialElevation, initialFocusError, initialZenithAngle));
  }

  private PartialFunction<Object, BoxedUnit> followingReceive(DoubleItem cElevation, DoubleItem cFocusError, DoubleItem cZenithAngle) {
    return ReceiveBuilder.
      match(StopFollowing.class, t -> {
        // do nothing
      }).
      match(UpdatedEventData.class, t -> {
        log.info("Got an Update Event: " + t);
        // Not really using the time here
        // Units checks - should not happen, so if so, flag an error and skip calculation
        if (t.zenithAngle.units() != ac.zenithAngleUnits || t.focusError.units() != ac.focusErrorUnits) {
          log.error("Ignoring event data received with improper units: zenithAngle: " + t.zenithAngle.units() + ", focusError: " + t.focusError.units());
        } else if (!verifyZenithAngle(t.zenithAngle) || !verifyFocusError(calculationConfig, t.focusError)) {
          log.error("Ignoring out of range event data: zenithAngle: " + t.zenithAngle + ", focusError: " + t.focusError);
        } else {
          // If inNSSMode is true, then we use angle 0.0
          // Do the calculation and send updates out
          double totalRangeDistance = focusZenithAngleToRangeDistance(calculationConfig, jvalue(cElevation), jvalue(t.focusError), jvalue(t.zenithAngle));

          double newElevation = rangeDistanceToElevation(totalRangeDistance, jvalue(t.zenithAngle));

          // Post a SystemEvent for AOESW if not inNSSMode according to spec
          if (!jvalue(inNSSMode)) {
            sendAOESWUpdate(jset(ac.naElevationKey, newElevation).withUnits(ac.naElevationUnits),
              jset(ac.naRangeDistanceKey, totalRangeDistance).withUnits(ac.naRangeDistanceUnits));
          }

          DoubleItem newTrombonePosition = calculateNewTrombonePosition(calculationConfig, cElevation, t.focusError, t.zenithAngle);

          // Send the new trombone stage position to the HCD
          sendTrombonePosition(ac.controlConfig, newTrombonePosition);

          // Post a StatusEvent for telemetry updates
          sendEngrUpdate(t.focusError, newTrombonePosition, t.zenithAngle);

          // Call again with new values - avoiding globals
          // I should be using newElevation, but it doesn't work well without changes in other values, so I'm not updating
          context().become(followingReceive(cElevation, t.focusError, t.zenithAngle));
        }
      }).
      match(SetElevation.class, t -> {
        // This updates the current elevation and then causes an internal update to move things
        log.info("Got elevation: " + t.elevation);
        // Restart the receive with the new value for elevation and the current values for others
        context().become(followingReceive(t.elevation, cFocusError, cZenithAngle));
        self().tell(new UpdatedEventData(cZenithAngle, cFocusError, new EventTime(Instant.now())), self());
      }).
      match(SetZenithAngle.class, t -> {
        // This updates the current zenith angle and then causes an internal update to move things
        log.info("FollowActor setting angle to: " + t.zenithAngle);
        // No need to call followReceive again since we are using the UpdateEventData message
        self().tell(new UpdatedEventData(t.zenithAngle, cFocusError, new EventTime(Instant.now())), self());
      }).
      matchAny(t -> log.warning("Unexpected message in TromboneAssembly:FollowActor: " + t)).
      build();
  }


  private DoubleItem calculateNewTrombonePosition(TromboneCalculationConfig calculationConfig, DoubleItem elevationIn,
                                                  DoubleItem focusErrorIn, DoubleItem zenithAngleIn) {
    double totalRangeDistance = focusZenithAngleToRangeDistance(calculationConfig, jvalue(elevationIn), jvalue(focusErrorIn), jvalue(zenithAngleIn));
    log.debug("totalRange: " + totalRangeDistance);

    double stagePosition = rangeDistanceToStagePosition(totalRangeDistance);
    return ac.spos(stagePosition);
  }

  //
  private void sendTrombonePosition(TromboneControlConfig controlConfig, DoubleItem stagePosition) {
    log.debug("Sending position: " + stagePosition);
    tromboneControl.ifPresent(actorRef -> actorRef.tell(new TromboneControl.GoToStagePosition(stagePosition), self()));
  }

  private void sendAOESWUpdate(DoubleItem elevationItem, DoubleItem rangeItem) {
    log.debug("Publish aoUpdate: $aoPublisher " + elevationItem + ", " + rangeItem);
    aoPublisher.ifPresent(actorRef -> actorRef.tell(new AOESWUpdate(elevationItem, rangeItem), self()));
  }

  private void sendEngrUpdate(DoubleItem focusError, DoubleItem trombonePosition, DoubleItem zenithAngle) {
    log.debug("Publish engUpdate: " + engPublisher);
    engPublisher.ifPresent(actorRef -> actorRef.tell(new EngrUpdate(focusError, trombonePosition, zenithAngle), self()));
  }

  // --- static defs ---

  // Props for creating the follow actor
  public static Props props(
    AssemblyContext assemblyContext,
    DoubleItem initialElevation,
    BooleanItem inNSSModeIn,
    Optional<ActorRef> tromboneControl,
    Optional<ActorRef> aoPublisher,
    Optional<ActorRef> engPublisher) {
    return Props.create(new Creator<FollowActor>() {
      private static final long serialVersionUID = 1L;

      @Override
      public FollowActor create() throws Exception {
        return new FollowActor(assemblyContext, initialElevation, inNSSModeIn, tromboneControl, aoPublisher, engPublisher);
      }
    });
  }

  /**
   * Messages received by csw.examples.vsliceJava.FollowActor
   * Update from subscribers
   */
  interface FollowActorMessages {}

  @SuppressWarnings("WeakerAccess")
  public static class UpdatedEventData implements FollowActorMessages {
    public final DoubleItem zenithAngle;
    public final DoubleItem focusError;
    public final EventTime time;

    public UpdatedEventData(DoubleItem zenithAngle, DoubleItem focusError, EventTime time) {
      this.zenithAngle = zenithAngle;
      this.focusError = focusError;
      this.time = time;
    }
  }

  // Messages to Follow Actor
  public static class SetElevation implements FollowActorMessages {
    public final DoubleItem elevation;

    public SetElevation(DoubleItem elevation) {
      this.elevation = elevation;
    }
  }

  @SuppressWarnings("WeakerAccess")
  public static class SetZenithAngle implements FollowActorMessages {
    public final DoubleItem zenithAngle;

    public SetZenithAngle(DoubleItem zenithAngle) {
      this.zenithAngle = zenithAngle;
    }
  }

  public static class StopFollowing implements FollowActorMessages {}
}

