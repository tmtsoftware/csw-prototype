package javacsw.services.loc;

import akka.actor.ActorRef;
import akka.actor.Props;
import csw.services.loc.LocationService;

import java.util.Set;

/**
 * Java API to creating a RegistrationTracker actor
 */
public class JRegistrationTracker {
    /**
     * Returns the Props needed to create an actor that tracks the registration of one or more connections and replies with a
     * ComponentRegistered message when done.
     *
     * @param registration Set of registrations to be registered with Location Service
     * @param replyTo      optional actorRef to reply to (usually self(), the parent of the actor)
     */
    public static Props props(Set<LocationService.Registration> registration, ActorRef replyTo) {
        return JLocationServiceSup.registrationTrackerProps(registration, replyTo);
    }
}
