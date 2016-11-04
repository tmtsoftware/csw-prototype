package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.events.EventServiceSettings;
import csw.services.loc.LocationService;
import csw.util.config.*;
import javacsw.services.events.IEventService;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import csw.util.config.Events.SystemEvent;
import csw.util.config.Events.StatusEvent;

import java.util.Optional;

import static javacsw.util.config.JItems.jadd;

/**
 * An actor that provides the publishing interface to the TMT Event Service and Telemetry Service.
 *
 * The TrombonePublisher receives messages from other actors that need to publish an event of some kind. The messages are
 * repackaged as SystemEvents or StatusEvents as needed.
 *
 * Currently, this actor publishes the sodiumLayer System Event for RTC, and the engr StatusEvent
 * and the state StatusEvent. The sodiumLayer event is triggered by the arrival of an AOESWUpdate message. The engr
 * StatusEvent is triggered by the arrival of an EngrUpdate message, and the state StatusEvent is triggered by the
 * TromboneState message.
 *
 * Values in messages are assumed to be correct and ready for publishing.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TrombonePublisher extends TromboneStateHandler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext assemblyContext;

  /**
   * An actor that provides the publishing interface to the TMT Event Service and Telemetry Service.
   *
   * The TrombonePublisher receives messages from other actors that need to publish an event of some kind. The messages are
   * repackaged as SystemEvents or StatusEvents as needed.
   *
   * Currently, this actor publishes the sodiumLayer System Event for RTC, and the engr StatusEvent
   * and the state StatusEvent. The sodiumLayer event is triggered by the arrival of an AOESWUpdate message. The engr
   * StatusEvent is triggered by the arrival of an EngrUpdate message, and the state StatusEvent is triggered by the
   * TromboneState message.
   *
   * Values in messages are assumed to be correct and ready for publishing.
   *
   * @param assemblyContext the trombone AssemblyContext contains important shared values and useful functions
   * @param eventServiceIn optional EventService for testing event service
   */
  public TrombonePublisher(AssemblyContext assemblyContext, Optional<IEventService> eventServiceIn) {
    this.assemblyContext = assemblyContext;

    log.info("Event Service in: " + eventServiceIn);

//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());

    getContext().become(publishingEnabled(eventServiceIn));

  }

  private PartialFunction<Object, BoxedUnit> publishingEnabled(Optional<IEventService> eventService) {
    return ReceiveBuilder.
      match(AOESWUpdate.class, t ->
          publishAOESW(eventService, t.naElevation, t.naRange)).
      match(EngrUpdate.class, t ->
          publishEngr(eventService, t.focusError, t.stagePosition, t.zenithAngle)).
      match(TromboneState.class, t ->
          publishState(eventService, t)).
      match(AxisStateUpdate.class, t ->
        publishAxisState(eventService, t.axisName, t.position, t.state, t.inLowLimit, t.inHighLimit, t.inHome)).
      match(AxisStatsUpdate.class, t ->
        publishAxisStats(eventService, t.axisName, t.initCount, t.moveCount, t.homeCount, t.limitCount, t.successCount, t.failCount, t.cancelCount)).
      match(LocationService.Location.class, t -> log.debug("Ignoring location: " + t)).
      matchAny(t -> log.warning("Unexpected message in TrombonePublisher:publishingEnabled: " + t)).
      build();
  }

  private void publishAOESW(Optional<IEventService> eventService, DoubleItem elevationItem, DoubleItem rangeItem) {
    SystemEvent se = jadd(new SystemEvent(assemblyContext.aoSystemEventPrefix), elevationItem, rangeItem);
    log.info("System publish of " + assemblyContext.aoSystemEventPrefix + ": " + se);
    eventService.ifPresent(e -> e.publish(se));
  }

  private void publishEngr(Optional<IEventService> eventService, DoubleItem rtcFocusError, DoubleItem stagePosition, DoubleItem zenithAngle) {
    StatusEvent ste = jadd(new StatusEvent(assemblyContext.engStatusEventPrefix), rtcFocusError, stagePosition, zenithAngle);
    log.info("Status publish of " + assemblyContext.engStatusEventPrefix + ": " + ste);
    eventService.ifPresent(e -> e.publish(ste));
  }

  private void publishState(Optional<IEventService> eventService, TromboneState ts) {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    StatusEvent ste = jadd(new StatusEvent(assemblyContext.tromboneStateStatusEventPrefix), ts.cmd, ts.move, ts.sodiumLayer, ts.nss);
    log.debug("Status state publish of " + assemblyContext.tromboneStateStatusEventPrefix + ": " + ste);
    eventService.ifPresent(e -> e.publish(ste));
  }

  private void publishAxisState(Optional<IEventService> eventService, StringItem axisName, IntItem position, ChoiceItem state, BooleanItem inLowLimit,
                                BooleanItem inHighLimit, BooleanItem inHome) {
    StatusEvent ste = jadd(new StatusEvent(assemblyContext.axisStateEventPrefix), axisName, position, state, inLowLimit, inHighLimit, inHome);
    log.debug("Axis state publish of " + assemblyContext.axisStateEventPrefix + ": " + ste);
    eventService.ifPresent(e -> e.publish(ste));
  }

  void publishAxisStats(Optional<IEventService> eventService, StringItem axisName, IntItem datumCount, IntItem moveCount, IntItem homeCount, IntItem limitCount,
                        IntItem successCount, IntItem failureCount, IntItem cancelCount) {
    StatusEvent ste = jadd(new StatusEvent(assemblyContext.axisStatsEventPrefix), axisName, datumCount, moveCount, homeCount, limitCount,
        successCount, failureCount, cancelCount);
    log.debug("Axis stats publish of " + assemblyContext.axisStatsEventPrefix + ": " + ste);
    eventService.ifPresent(e -> e.publish(ste));
  }

  // --- static defs ---

  public static Props props(AssemblyContext assemblyContext, Optional<IEventService> eventServiceIn) {
    return Props.create(new Creator<TrombonePublisher>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TrombonePublisher create() throws Exception {
        return new TrombonePublisher(assemblyContext, eventServiceIn);
      }
    });
  }

  /**
   * Used by actors wishing to cause an event for AO ESW
   */
  public static class AOESWUpdate {
    public final DoubleItem naElevation;
    public final DoubleItem naRange;

    /**
     * Constructor
     * @param naElevation elevation update
     * @param naRange range update
     */
    public AOESWUpdate(DoubleItem naElevation, DoubleItem naRange) {
      this.naElevation = naElevation;
      this.naRange = naRange;
    }
  }

  /**
   * Used by actors wishing to cause an engineering event update
   */
  public static class EngrUpdate {
    public final DoubleItem focusError;
    public final DoubleItem stagePosition;
    public final DoubleItem zenithAngle;

    /**
     * Constructor
     * @param focusError focus error value as DoubleItem
     * @param stagePosition stage position as a DoubleItem
     * @param zenithAngle zenith angle update as a DoubleItem
     */
    public EngrUpdate(DoubleItem focusError, DoubleItem stagePosition, DoubleItem zenithAngle) {
      this.focusError = focusError;
      this.stagePosition = stagePosition;
      this.zenithAngle = zenithAngle;
    }
  }

  public static class AxisStateUpdate {
    public final StringItem axisName;
    public final IntItem position;
    public final ChoiceItem state;
    public final BooleanItem inLowLimit;
    public final BooleanItem inHighLimit;
    public final BooleanItem inHome;

    public AxisStateUpdate(StringItem axisName, IntItem position, ChoiceItem state, BooleanItem inLowLimit,
                           BooleanItem inHighLimit, BooleanItem inHome) {
      this.axisName = axisName;
      this.position = position;
      this.state = state;
      this.inLowLimit = inLowLimit;
      this.inHighLimit = inHighLimit;
      this.inHome = inHome;
    }
  }

  public static class AxisStatsUpdate {
    public final StringItem axisName;
    public final IntItem initCount;
    public final IntItem moveCount;
    public final IntItem homeCount;
    public final IntItem limitCount;
    public final IntItem successCount;
    public final IntItem failCount;
    public final IntItem cancelCount;

    public AxisStatsUpdate(StringItem axisName, IntItem initCount, IntItem moveCount, IntItem homeCount,
                           IntItem limitCount, IntItem successCount, IntItem failCount, IntItem cancelCount) {
      this.axisName = axisName;
      this.initCount = initCount;
      this.moveCount = moveCount;
      this.homeCount = homeCount;
      this.limitCount = limitCount;
      this.successCount = successCount;
      this.failCount = failCount;
      this.cancelCount = cancelCount;
    }
  }
}

