package javacsw.services.ts;

import akka.event.LoggingAdapter;
import csw.services.ts.JavaTimeServiceScheduler;
import csw.services.ts.TimeService;

/**
 * A java friendly version of {@link TimeService.TimeServiceScheduler}.
 * <p>
 * TimeServiceSchedule provides a component actor with timed messages.
 * The following methods are available to derived classes:
 * <p>
 * {@link TimeService.TimeServiceScheduler#scheduleOnce} - sends a message to an actor once some time in the future
 * <p>
 * {@link TimeService.TimeServiceScheduler#schedule} - waits until a specific time and then sends periodic message to an actor until cancelled
 */
public abstract class JTimeServiceScheduler extends JavaTimeServiceScheduler {
    /**
     * Reference to the actor's logger
     */
    public LoggingAdapter log = log();
}
