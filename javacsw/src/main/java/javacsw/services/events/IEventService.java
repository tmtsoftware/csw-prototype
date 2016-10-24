package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import csw.services.events.EventService;
import csw.services.events.EventService.*;
import csw.services.events.EventServiceSettings;
import csw.util.config.Events.EventServiceEvent;
import scala.Unit;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A Java interface for a non-blocking key / value store. This class does not wait for operations to complete
 * and returns Futures as results.
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public interface IEventService {
    /**
     * @param system the actor system used to access the akka config file containing the kvs settings
     * @return an object containing the kvs settings
     */
    static EventServiceSettings getEventServiceSettings(ActorSystem system) {
        return EventServiceSettings.getEventServiceSettings(system);
    }

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IEventService
     */
    static IEventService getEventService(EventServiceSettings settings, ActorRefFactory system) {
        return new JEventService(settings, system);
    }


    /**
     * Publishes the value for the given key
     *
     * @param event   the event to publish
     */
    CompletableFuture<Unit> publish(EventServiceEvent event);

    /**
     * API to handle an event from the event service
     */
    interface EventHandler {
        void handleEvent(EventServiceEvent event);
    }

    /**
     * Subscribes an actor or callback function to events matching the given prefixes
     * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
     *
     * @param subscriber an optional actor to receive Event messages
     * @param callback   an optional callback which will be called with Event objects (in another thread)
     * @param prefixes   one or more prefixes of events, may include wildcard
     */
    EventMonitor subscribe(Optional<ActorRef> subscriber, Optional<EventHandler> callback, String... prefixes);
}
