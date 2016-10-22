package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.events.*;
import csw.util.config.Configurations.ConfigKey;
import csw.util.config.Events.*;
import csw.util.config.*;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Optional;

import static javacsw.util.config.JItems.jitem;
import static javacsw.util.config.JItems.jvalue;

/**
 * TMT Source Code: 6/20/16.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TromboneEventSubscriber extends JAbstractSubscriber {
  // XXX FIXME: Replace JAbstractSubscriber usage with call to eventService.subscribe()!

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final BooleanItem nssInUseIn;
  private final Optional<ActorRef> followActor;
//  private final Optional<EventServiceSettings> eventServiceSettings;

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

  private TromboneEventSubscriber(AssemblyContext ac, BooleanItem nssInUseIn, Optional<ActorRef> followActor,
                                 Optional<EventServiceSettings> eventServiceSettings) {
    this.ac = ac;
    this.nssInUseIn = nssInUseIn;
    this.followActor = followActor;
//    this.eventServiceSettings = eventServiceSettings;

    nssZenithAngle = ac.za(0.0);
    initialZenithAngle = jvalue(nssInUseIn) ? nssZenithAngle : ac.za(0.0);
    initialFocusError = ac.fe(0.0);
    nssInUseGlobal = nssInUseIn;


//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());

    context().become(subscribeReceive(nssInUseIn, initialZenithAngle, initialFocusError));

  }


  @Override
  public void preStart() {
    // Allways subscribe to focus error
    subscribeKeys(ac.feConfigKey);

    log.info("nssInuse: " + nssInUseIn);

    // But only subscribe to ZA if nss is not in use
    if (!jvalue(nssInUseIn)) {
      // NSS not inuse so subscribe to ZA
      subscribeKeys(ac.zaConfigKey);
    }
  }

  PartialFunction<Object, BoxedUnit> subscribeReceive(BooleanItem cNssInUse, DoubleItem cZenithAngle, DoubleItem cFocusError) {
    return ReceiveBuilder.
      match(SystemEvent.class, event -> {
        if (event.info().source().equals(ac.zaConfigKey)) {
          DoubleItem newZenithAngle = jitem(event, ac.zenithAngleKey);
          log.info("Received ZA: " + event);
          updateFollowActor(newZenithAngle, cFocusError, event.info().time());
          // Pass the new values to the next message
          context().become(subscribeReceive(cNssInUse, newZenithAngle, cFocusError));
        } else if (event.info().source().equals(ac.feConfigKey)) {
          // Update focusError state and then update calculator
          log.info("Received FE: " + event);
          DoubleItem newFocusError = jitem(event, ac.focusErrorKey);
          updateFollowActor(cZenithAngle, newFocusError, event.info().time());
          // Pass the new values to the next message
          context().become(subscribeReceive(cNssInUse, cZenithAngle, newFocusError));
        }
      }).
      match(FollowActor.StopFollowing.class, t -> {
        // Kill this subscriber
        context().stop(self());
      }).
      match(UpdateNssInUse.class, t -> {
        BooleanItem nssInUseUpdate = t.nssInUse;
        if (nssInUseUpdate != cNssInUse) {
          if (jvalue(nssInUseUpdate)) {
            unsubscribeKeys(ac.zaConfigKey);
            context().become(subscribeReceive(nssInUseUpdate, nssZenithAngle, cFocusError));
          } else {
            subscribeKeys(ac.zaConfigKey);
            context().become(subscribeReceive(nssInUseUpdate, cZenithAngle, cFocusError));
          }
          // Need to update the global for shutting down event subscriptions
          nssInUseGlobal = nssInUseUpdate;
        }
      }).
      matchAny(t -> log.warning("Unexpected message received in TromboneEventSubscriber:subscribeReceive: " + t)).
      build();
  }

  private void unsubscribeKeys(ConfigKey... configKeys) {
    for (ConfigKey configKey : configKeys) {
      log.info("Unsubscribing from: " + configKey);
      unsubscribe(configKey.prefix());
    }
  }

  private void subscribeKeys(ConfigKey... configKeys) {
    for (ConfigKey configKey : configKeys) {
      log.info("Subscribing to: " + configKey);
      subscribe(configKey.prefix());
    }
  }

  @Override
  public void postStop() {
    if (!jvalue(nssInUseGlobal)) {
      unsubscribeKeys(ac.zaConfigKey);
    }
    unsubscribeKeys(ac.feConfigKey);
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
   * @param followActor          a FollowActor as an Option[ActorRef]
   * @param eventServiceSettings for testing, an event Service Settings can be provided
   * @return Props for TromboneEventSubscriber
   */
  public static Props props(AssemblyContext ac, BooleanItem nssInUseIn, Optional<ActorRef> followActor,
                            Optional<EventServiceSettings> eventServiceSettings) {
    return Props.create(new Creator<TromboneEventSubscriber>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneEventSubscriber create() throws Exception {
        return new TromboneEventSubscriber(ac, nssInUseIn, followActor, eventServiceSettings);
      }
    });
  }

  public static class UpdateNssInUse {
    public final BooleanItem nssInUse;

    public UpdateNssInUse(BooleanItem nssInUse) {
      this.nssInUse = nssInUse;
    }
  }
}

