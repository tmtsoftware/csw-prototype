package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.events.*;
import csw.services.loc.Connection;
import csw.services.loc.LocationService;
import csw.services.loc.LocationService.Location;
import csw.services.loc.LocationService.ResolvedTcpLocation;
import csw.util.config.Configurations.ConfigKey;
import csw.util.config.Events.*;
import csw.util.config.*;
import javacsw.services.events.IEventService;
import javacsw.services.pkg.ILocationSubscriberClient;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import csw.services.events.EventService.EventMonitor;

import java.util.Arrays;
import java.util.Optional;

import static javacsw.util.config.JItems.jitem;
import static javacsw.util.config.JItems.jvalue;

/**
 * TMT Source Code: 6/20/16.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TromboneEventSubscriber extends AbstractActor implements ILocationSubscriberClient {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final BooleanItem nssInUseIn;
  private final Optional<ActorRef> followActor;

  // If state of NSS is false, then subscriber provides 0 for zenith distance with updates to subscribers

  // This value is used when NSS is in Use
  private final DoubleItem nssZenithAngle;

  // Kim possibly set these initial values from config or get them from telemetry store
  // These vars are needed since updates from RTC and TCS will happen at different times and we need both values
  // Could have two events but that requries follow actor to keep values
  private final DoubleItem initialZenithAngle;
  private DoubleItem initialFocusError;
  // This is used to keep track since it can be updated
  private BooleanItem nssInUseGlobal;

  // This var is needed to capture the Monitor used for subscriptions
  private EventService.EventMonitor subscribeMonitor;


  private TromboneEventSubscriber(AssemblyContext ac, BooleanItem nssInUseIn, Optional<ActorRef> followActor,
                                  Optional<IEventService> eventServiceIn) {
    this.ac = ac;
    this.nssInUseIn = nssInUseIn;
    this.followActor = followActor;

    nssZenithAngle = ac.za(0.0);
    initialZenithAngle = jvalue(nssInUseIn) ? nssZenithAngle : ac.za(0.0);
    initialFocusError = ac.fe(0.0);
    nssInUseGlobal = nssInUseIn;


//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());

    context().become(connectingReceive());

  }

  private PartialFunction<Object, BoxedUnit> connectingReceive() {
    return ReceiveBuilder.
      match(Location.class, location -> {
        if (location instanceof LocationService.ResolvedTcpLocation) {
          ResolvedTcpLocation t = (ResolvedTcpLocation) location;
          // Verify that it is the event service
          Connection.TcpConnection conn = IEventService.eventServiceConnection(IEventService.defaultName);
          if (location.connection().equals(conn)) {
            log.debug("Event subscriber received connection: " + t);
            IEventService eventService = IEventService.getEventService(t.host(), t.port(), context().system());
            startupSubscriptions(eventService);
            context().become(subscribeReceive(eventService, nssInUseIn, initialZenithAngle, initialFocusError));
          }
        } else log.debug("EventSubscriber received location: " + location);
      }).
      build();
  }

  private void startupSubscriptions(IEventService eventService) {
    // Always subscribe to focus error
    // Create the subscribeMonitor here
    subscribeMonitor = subscribeKeys(eventService, ac.feConfigKey);
    log.info("FeMonitor actor: " + subscribeMonitor.actorRef());

    log.info("nssInuse: " + nssInUseIn);

    // But only subscribe to ZA if nss is not in use
    if (!jvalue(nssInUseIn)) {
      // NSS not inuse so subscribe to ZA
      subscribeKeys(subscribeMonitor, ac.zaConfigKey);
    }
  }

  private PartialFunction<Object, BoxedUnit> subscribeReceive(IEventService eventService, BooleanItem cNssInUse, DoubleItem cZenithAngle, DoubleItem cFocusError) {
    return ReceiveBuilder.
      match(SystemEvent.class, event -> {
        if (event.info().source().equals(ac.zaConfigKey)) {
          DoubleItem newZenithAngle = jitem(event, ac.zenithAngleKey);
          log.info("Received ZA: " + event);
          updateFollowActor(newZenithAngle, cFocusError, event.info().time());
          // Pass the new values to the next message
          context().become(subscribeReceive(eventService, cNssInUse, newZenithAngle, cFocusError));
        } else if (event.info().source().equals(ac.feConfigKey)) {
          // Update focusError state and then update calculator
          log.info("Received FE: " + event);
          DoubleItem newFocusError = jitem(event, ac.focusErrorKey);
          updateFollowActor(cZenithAngle, newFocusError, event.info().time());
          // Pass the new values to the next message
          context().become(subscribeReceive(eventService, cNssInUse, cZenithAngle, newFocusError));
        } else log.info("Got some other event: " + event.info().source());
      }).
      match(FollowActor.StopFollowing.class, t -> {
        // Kill this subscriber
        context().stop(self());
      }).
      // This is an engineering command to allow checking subscriber
        match(UpdateNssInUse.class, t -> {
        BooleanItem nssInUseUpdate = t.nssInUse;
        if (nssInUseUpdate != cNssInUse) {
          if (jvalue(nssInUseUpdate)) {
            unsubscribeKeys(subscribeMonitor, ac.zaConfigKey);
            context().become(subscribeReceive(eventService, nssInUseUpdate, nssZenithAngle, cFocusError));
          } else {
            subscribeKeys(subscribeMonitor, ac.zaConfigKey);
            context().become(subscribeReceive(eventService, nssInUseUpdate, cZenithAngle, cFocusError));
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
      monitor.unsubscribe(configKey.prefix());
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
      monitor.subscribe(configKey.prefix());
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
                            Optional<IEventService> eventService) {
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

