package javacsw.services.pkg;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;
import csw.services.loc.LocationService.Location;
import akka.actor.AbstractActor.Receive;

/**
 * LocationSubscriberClient can be used to receive updates to Locations.
 *
 * The message received is a LocationService.Location, which can be a ResolvedAkkLocation, ResolvedHttpLocation, or a ResolvedServiceLocation
 *
 * Note: Implementers of this interface should call: subscribeToLocationUpdates();
 * to Indicate that they want location updates (Done automatically in Scala version).
 */
@SuppressWarnings("unused")
public interface ILocationSubscriberClient extends Actor {
//  /**
//   * An Akka receive partial function that can be used rather than receiving the Location message in your
//   * own code.
//   * @return Receive partial function
//   */
//  default Receive locationSubscriberReceive() { // XXX not used
//    return ReceiveBuilder.
//      match(Location.class, this::locationUpdate).
//      build();
//  }

  /**
   * Start receiving location updates.  It is necessary to call this in the client when you are ready to receive updates.
   */
  default void subscribeToLocationUpdates() {
    context().system().eventStream().subscribe(self(), Location.class);
  }

  /**
   * The given actor stops listening to Location updates.
   */
  default void unsubscribeLocationUpdates() {
    context().system().eventStream().unsubscribe(self());
  }

  /**
   * If calling the TrackerSubscriberClient recieve, then override this method to handle Location events.
   * @param location a resolved Location; either HTTP or Akka
   */
  default void locationUpdate(Location location) {}
}
