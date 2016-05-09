package javacsw.services.loc;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Java API for creating a LocationTracker actor
 */
@SuppressWarnings("unused")
public class JLocationTracker {
    /**
     * Used to create the LocationTracker, an actor that notifies the replyTo actor when all the
     * requested services are available.
     * If all services are available, a ServicesReady message is sent. If any of the requested
     * services stops being available, a Disconnected messages is sent.
     *
     * @param replyTo optional actorRef to reply to (default: parent of this actor)
     * @return the props needed to create the actor
     */
    public static Props props(ActorRef replyTo) {
        return JLocationServiceSup.locationTrackerProps(replyTo);
    }
}
