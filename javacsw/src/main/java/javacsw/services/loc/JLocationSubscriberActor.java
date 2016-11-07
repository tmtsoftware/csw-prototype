package javacsw.services.loc;

import csw.services.loc.LocationSubscriberActor;

/**
 * Java API for Scala TrackerSubscriber
 */
public class JLocationSubscriberActor {
  /**
   * Message sent to begin receiving Location events
   */
  public static final LocationSubscriberActor.LocationSubscriberMessages Subscribe  = LocationSubscriberActor.Subscribe$.MODULE$;

  /**
   * Message sent to stop receiving Location events
   */
  public static final LocationSubscriberActor.LocationSubscriberMessages Unsubscribe  = LocationSubscriberActor.Unsubscribe$.MODULE$;
}
