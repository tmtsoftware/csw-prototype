package javacsw.services.events;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import csw.services.events.EventService;
import csw.services.events.EventService.*;
import csw.services.events.EventServiceSettings;
import csw.util.config.Events.EventServiceEvent;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Optional;

/**
 * A blocking Java interface for the event service. This class blocks and waits for operations to complete
 * (rather than returning Futures as results).
 */
@SuppressWarnings({"unused", "OptionalUsedAsFieldOrParameterType"})
public interface IBlockingEventService {

    /**
     * @param settings Redis server settings
     * @param system   Akka env required by RedisClient
     * @return a new IBlockingEventService for StatusEvent objects
     */
    static IBlockingEventService getEventService(Duration timeout, EventServiceSettings settings, ActorRefFactory system) {
        return new JBlockingEventService(timeout, settings, system);
    }

    /**
     * Publishes the given event
     *
     * @param event the event to publish
     */
    void publish(EventServiceEvent event);

    /**
     * Publishes the given event
     *
     * @param event the event to publish
     * @param n     the max number of history events to keep (0 means no history)
     */
    void publish(EventServiceEvent event, int n);

    /**
     * Subscribes an actor or callback function to events matching the given prefixes
     * Each prefix may be followed by a '*' wildcard to subscribe to all matching events.
     *
     * @param subscriber an optional actor to receive Event messages
     * @param callback   an optional callback which will be called with Event objects (in another thread)
     * @param prefixes   one or more prefixes of events, may include wildcard
     */
    EventMonitor subscribe(Optional<ActorRef> subscriber, Optional<IEventService.EventHandler> callback, String... prefixes);

    /**
     * Gets the latest event for the given prefix
     *
     * @param prefix the key
     * @return the result, None if the key was not found
     */
    Optional<EventServiceEvent> get(String prefix);

    /**
     * Returns a list containing up to the last n events for the given prefix
     *
     * @param prefix the event prefix to use
     * @param n   max number of history events to return
     * @return list of the last n events
     */
    List<EventServiceEvent> getHistory(String prefix, int n);

    /**
     * Deletes the event with the given prefix
     *
     * @param prefix the prefix for the event to delete
     * @return true if an event was deleted
     */
    boolean delete(String prefix);

    /**
     * Disconnects from the event service
     */
    void  disconnect();

    /**
     * Shuts the event service down
     */
    void shutdown();
}
