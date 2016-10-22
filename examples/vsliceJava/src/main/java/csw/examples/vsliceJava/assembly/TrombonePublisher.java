package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.events.EventServiceSettings;
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

  private final AssemblyContext ac;
  private final IEventService eventService;

  /**
   * Constructor
   *
   * @param ac the trombone AssemblyContext contains important shared values and useful functions
   * @param settings optional paramters for connecting to a testing event service
   */
  public TrombonePublisher(AssemblyContext ac, Optional<EventServiceSettings> settings) {
    this.ac = ac;

    eventService = IEventService.getEventService(
      settings.orElse(IEventService.getEventServiceSettings(context().system())),
      context().system());

//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());

    getContext().become(publishingEnabled());

  }

  PartialFunction<Object, BoxedUnit> publishingEnabled() {
    return ReceiveBuilder.
      match(AOESWUpdate.class, t -> publishAOESW(t.naElevation, t.naRange)).
      match(EngrUpdate.class, t -> publishEngr(t.focusError, t.stagePosition, t.zenithAngle)).
      match(TromboneState.class, this::publishState).
      match(AxisStateUpdate.class, t -> publishAxisState(t.axisName, t.position, t.state, t.inLowLimit, t.inHighLimit, t.inHome)).
      match(AxisStatsUpdate.class, t -> publishAxisStats(t.axisName, t.initCount, t.moveCount, t.homeCount, t.limitCount,
        t.successCount, t.failCount, t.cancelCount)).
      matchAny(t -> log.warning("Unexpected message in TrombonePublisher:publishingEnabled: " + t)).
      build();
  }

  private void publishAOESW(DoubleItem elevationItem, DoubleItem rangeItem) {
    SystemEvent se = jadd(new SystemEvent(ac.aoSystemEventPrefix), elevationItem, rangeItem);
    log.info("System publish of " + ac.aoSystemEventPrefix + ": " + se);
    eventService.publish(se);
  }

  private void publishEngr(DoubleItem rtcFocusError, DoubleItem stagePosition, DoubleItem zenithAngle) {
    StatusEvent ste = jadd(new StatusEvent(ac.engStatusEventPrefix), rtcFocusError, stagePosition, zenithAngle);
    log.info("Status publish of " + ac.engStatusEventPrefix + ": " + ste);
    eventService.publish(ste);
  }

  private void publishState(TromboneState ts) {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    StatusEvent ste = jadd(new StatusEvent(ac.tromboneStateStatusEventPrefix), ts.cmd, ts.move, ts.sodiumLayer, ts.nss);
    log.debug("Status state publish of " + ac.tromboneStateStatusEventPrefix + ": " + ste);
    eventService.publish(ste);
  }

  private void publishAxisState(StringItem axisName, IntItem position, ChoiceItem state, BooleanItem inLowLimit,
                                BooleanItem inHighLimit, BooleanItem inHome) {
    StatusEvent ste = jadd(new StatusEvent(ac.axisStateEventPrefix), axisName, position, state, inLowLimit, inHighLimit, inHome);
    log.debug("Axis state publish of " + ac.axisStateEventPrefix + ": " + ste);
    eventService.publish(ste);
  }

  void publishAxisStats(StringItem axisName, IntItem datumCount, IntItem moveCount, IntItem homeCount, IntItem limitCount,
                        IntItem successCount, IntItem failureCount, IntItem cancelCount) {
    StatusEvent ste = jadd(new StatusEvent(ac.axisStatsEventPrefix), axisName, datumCount, moveCount, homeCount, limitCount,
        successCount, failureCount, cancelCount);
    log.debug("Axis stats publish of " + ac.axisStatsEventPrefix + ": " + ste);
    eventService.publish(ste);
  }

  // --- static defs ---

  public static Props props(AssemblyContext ac, Optional<EventServiceSettings> settings) {
    return Props.create(new Creator<TrombonePublisher>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TrombonePublisher create() throws Exception {
        return new TrombonePublisher(ac, settings);
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

