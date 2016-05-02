package javacsw.services.event;

import csw.services.event.EventService;
import csw.services.event.EventServiceSettings;
import csw.util.cfg.Events;
import csw.util.cfg.Events.EventServiceEvent;
import scala.concurrent.duration.FiniteDuration;

/**
 * Java API for the publisher side of the (Hornetq based) Event Service.
 * See {@link JEventSubscriber} for the subscriber side.
 */
public class JEventService {
    private final EventService eventService;

    /**
     * Initialize from the settings in resources.conf or application.conf
     * @param prefix the prefix for the events that will be published
     * @param settings the settings for connecting to the server
     */
    public JEventService(String prefix, EventServiceSettings settings) {
        eventService = EventService.apply(prefix, settings);
    }

    /**
     * Publishes the given event (channel is the event prefix).
     * @param event the event to publish
     * @param expire time to live for event
     */
    public void publish(EventServiceEvent event, FiniteDuration expire) {
        eventService.publish(event, expire);
    }

    /**
     * Closes the connection to the server.
     */
    public void close() {
        eventService.close();
    }
}
