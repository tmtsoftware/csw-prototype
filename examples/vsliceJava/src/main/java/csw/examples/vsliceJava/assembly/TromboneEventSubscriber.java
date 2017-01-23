package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.events.EventService;
import csw.util.config.BooleanItem;
import csw.util.config.DoubleItem;
import javacsw.services.events.IEventService;
import javacsw.services.pkg.ILocationSubscriberClient;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Arrays;
import java.util.Optional;

import static csw.services.events.EventService.EventMonitor;
import static csw.util.config.Configurations.ConfigKey;
import static csw.util.config.Events.EventTime;
import static csw.util.config.Events.SystemEvent;
import static javacsw.util.config.JItems.jitem;
import static javacsw.util.config.JItems.jvalue;

/**
 * TMT Source Code: 6/20/16.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "FieldCanBeLocal"})
public class TromboneEventSubscriber extends AbstractActor implements ILocationSubscriberClient {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final BooleanItem nssInUseIn;
  private final Optional<ActorRef> followActor;

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers

  // This value is used when NSS is in Use
  final DoubleItem nssZenithAngle;

  // Kim possibly set these initial values from config or get them from telemetry store
  // These vars are needed since updates from RTC and TCS will happen at different times and we need both values
  // Could have two events but that requries follow actor to keep values
  final DoubleItem initialZenithAngle;
  DoubleItem initialFocusError;
  // This is used to keep track since it can be updated
  BooleanItem nssInUseGlobal;

  // This var is needed to capture the Monitor used for subscriptions
  private final EventService.EventMonitor subscribeMonitor;


  private TromboneEventSubscriber(AssemblyContext ac, BooleanItem nssInUseIn, Optional<ActorRef> followActor,
                                  IEventService eventService) {
    subscribeToLocationUpdates();
    this.ac = ac;
    this.nssInUseIn = nssInUseIn;
    this.followActor = followActor;

    nssZenithAngle = ac.za(0.0);
    initialZenithAngle = jvalue(nssInUseIn) ? nssZenithAngle : ac.za(0.0);
    initialFocusError = ac.fe(0.0);
    nssInUseGlobal = nssInUseIn;

    // This var is needed to capture the Monitor used for subscriptions
    subscribeMonitor = startupSubscriptions(eventService);

    receive(subscribeReceive(nssInUseIn, initialZenithAngle, initialFocusError));
  }

  private EventMonitor startupSubscriptions(IEventService eventService) {
    // Always subscribe to focus error
    // Create the subscribeMonitor here
    EventMonitor subscribeMonitor = subscribeKeys(eventService, ac.feConfigKey);
    log.info("FeMonitor actor: " + subscribeMonitor.actorRef());

    log.info("nssInuse: " + nssInUseIn);

    // But only subscribe to ZA if nss is not in use
    if (!jvalue(nssInUseIn)) {
      // NSS not inuse so subscribe to ZA
      subscribeKeys(subscribeMonitor, ac.zaConfigKey);
    }
    return subscribeMonitor;
  }

  private PartialFunction<Object, BoxedUnit> subscribeReceive(BooleanItem cNssInUse, DoubleItem cZenithAngle, DoubleItem cFocusError) {
    return ReceiveBuilder.

      match(SystemEvent.class, event -> {
        if (event.info().source().equals(ac.zaConfigKey)) {
          DoubleItem newZenithAngle = jitem(event, ac.zenithAngleKey);
          log.info("Received ZA: " + event);
          updateFollowActor(newZenithAngle, cFocusError, event.info().eventTime());
          // Pass the new values to the next message
          context().become(subscribeReceive(cNssInUse, newZenithAngle, cFocusError));

        } else if (event.info().source().equals(ac.feConfigKey)) {
          // Update focusError state and then update calculator
          log.info("Received FE: " + event);
          DoubleItem newFocusError = jitem(event, ac.focusErrorKey);
          updateFollowActor(cZenithAngle, newFocusError, event.info().eventTime());
          // Pass the new values to the next message
          context().become(subscribeReceive(cNssInUse, cZenithAngle, newFocusError));

        } else log.info("GsubscribeReceive in TromboneEventSubscriber received an unknown SystemEvent: " + event.info().source());
      }).

      match(FollowActor.StopFollowing.class, t -> {
        subscribeMonitor.stop();
        // Kill this subscriber
        context().stop(self());
      }).

      // This is an engineering command to allow checking subscriber
       match(UpdateNssInUse.class, t -> {
        BooleanItem nssInUseUpdate = t.nssInUse;
        if (!nssInUseUpdate.equals(cNssInUse)) {
          if (jvalue(nssInUseUpdate)) {
            unsubscribeKeys(subscribeMonitor, ac.zaConfigKey);
            context().become(subscribeReceive(nssInUseUpdate, nssZenithAngle, cFocusError));
          } else {
            subscribeKeys(subscribeMonitor, ac.zaConfigKey);
            context().become(subscribeReceive(nssInUseUpdate, cZenithAngle, cFocusError));
          }
          // Need to update the global for shutting down event subscriptions
          nssInUseGlobal = nssInUseUpdate;
        }
      }).

        matchAny(t -> log.warning("Unexpected message received in TromboneEventSubscriber:subscribeReceive: " + t)).
        build();
  }


  private void unsubscribeKeys(EventMonitor monitor, ConfigKey... configKeys) {
    log.debug("Unsubscribing to: " + Arrays.toString(configKeys));
    for(ConfigKey configKey : configKeys) {
      monitor.unsubscribeFrom(configKey.prefix());
    }
  }

  private EventMonitor subscribeKeys(IEventService eventService, ConfigKey... configKeys) {
    log.debug("Subscribing to: " + Arrays.toString(configKeys));
    // XXX TODO: use streams
    String[] prefixes = new String[configKeys.length];
    for (int i = 0; i < configKeys.length; i++) {
      prefixes[i] = configKeys[i].prefix();
    }
    return eventService.subscribe(self(), false, prefixes);
  }

  private void subscribeKeys(EventMonitor monitor, ConfigKey... configKeys) {
    log.debug("Subscribing to: " + Arrays.toString(configKeys));
    for(ConfigKey configKey : configKeys) {
      monitor.subscribeTo(configKey.prefix());
    }
  }

  /**
   * This function is called whenever a new event arrives. The function takes the current information consisting of
   * the zenithAngle and focusError which is actor state and forwards it to the FoolowActor if present
   *
   * @param eventTime - the time of the last event update
   */
  private void updateFollowActor(DoubleItem zenithAngle, DoubleItem focusError, EventTime eventTime) {
    followActor.ifPresent(actoRef -> actoRef.tell(new FollowActor.UpdatedEventData(zenithAngle, focusError, eventTime), self()));
  }

  // --- static defs ---

  /**
   * props for the TromboneEventSubscriber
   *
   * @param followActor  a FollowActor as an Option[ActorRef]
   * @param eventService for testing, an event Service Settings can be provided
   * @return Props for TromboneEventSubscriber
   */
  public static Props props(AssemblyContext ac, BooleanItem nssInUseIn, Optional<ActorRef> followActor,
                            IEventService eventService) {
    return Props.create(new Creator<TromboneEventSubscriber>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneEventSubscriber create() throws Exception {
        return new TromboneEventSubscriber(ac, nssInUseIn, followActor, eventService);
      }
    });
  }

  public static class UpdateNssInUse {
    final BooleanItem nssInUse;

    public UpdateNssInUse(BooleanItem nssInUse) {
      this.nssInUse = nssInUse;
    }
  }
}

