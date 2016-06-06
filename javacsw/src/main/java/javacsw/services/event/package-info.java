/**
 * This project implements an event service based on Akka and HornetQ.
 * <p>
 * <strong>Event Publisher</strong>
 * <p>
 * The {@link javacsw.services.event.JEventService} class provides the method <code>publish(event)</code> to publish an
 * object of type {@link csw.util.config.Events.EventServiceEvent} (base trait/interface for events).
 * <p>
 * <strong>Event Subscriber</strong>
 * <p>
 * The abstract {@link javacsw.services.event.JEventSubscriber} class adds the method `subscribe(prefix)`.
 * After calling this method, the subscribing actor will receive all Event messages published for the prefix.
 * You can use wildcards in the prefix string.
 * For example <code>tmt.mobie.red.dat.*</code> or <code>tmt.mobie.red.dat.#</code>, where * matches a single word and # matches
 * multiple words separated by dots. Subscribers should be careful not to block when receiving messages,
 * so that the actor queue does not fill up too much.
 */
package javacsw.services.event;