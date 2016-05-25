/**
 * This project implements the CSW Time Service based on Java 8 java.time and Akka.
 * <p>
 * Time Service provides basic time access and a neutral API around the Akka scheduling routines.
 * Accessing the time API does not require an actor, but the scheduling routines
 * are assumed to be actors. The java abstract scheduling class to extend is {@link javacsw.services.ts.JTimeServiceScheduler}.
 * <p>
 * <strong>Time Access</strong>
 * <p>
 * Time Access can be used by importing {@link javacsw.services.ts.JTimeService}.
 * It includes time and date/time routines for local time, UTC, TAI, and local time in Hawaii.
 * <p>
 * <strong>TimeServiceScheduler</strong>
 * <p>
 * The {@link javacsw.services.ts.JTimeServiceScheduler} class provides the two methods `scheduleOnce(startTime, receiver, message)`
 * and `schedule(startTime, period, receiver, message)`.
 * <p>
 * scheduleOnce will send a specified message to the specified receiver once at the given start time.
 * schedule will send a specified message to the specified receiver with the period starting at the
 * provided startTime.
 */
package javacsw.services.ts;