package javacsw.util.param;

import csw.util.akka.PublisherActor.*;

/**
 * Java API for PublisherActor messages
 */
@SuppressWarnings("unused")
public class JPublisherActor {
  /**
   * Subscribes the sender
   */
  public static final PublisherActorMessage Subscribe = Subscribe$.MODULE$;

  /**
   * Unsubscribes the sender
   */
  public static final PublisherActorMessage Unsubscribe = Unsubscribe$.MODULE$;

  /**
   * Message requesting the publisher to publish the current values
   */
  public static final PublisherActorMessage RequestCurrent = RequestCurrent$.MODULE$;
}
