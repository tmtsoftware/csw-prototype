package javacsw.services.ts;

import akka.actor.ActorLogging;
import akka.actor.UntypedActor;
import akka.event.LoggingAdapter;
import csw.services.ts.JavaTimeServiceScheduler;
import csw.services.ts.TimeService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Java API to the CSW Time Service.
 */
public class JTimeService {
    /**
     * Returns the local time in the current time zone.
     *
     * @return a LocalTime now value.
     */
    public static LocalTime localTimeNow() {
        return TimeService.localTimeNow();
    }

    /**
     * Returns the local date and time in the current time zone.
     *
     * @return a LocalDateTime now value.
     */
    public static LocalDateTime localTimeDateNow() {
        return TimeService.localTimeDateNow();
    }

    /**
     * Returns the local time now in Hawaii
     *
     * @return a LocalTime now value in the "US/Pacific" zone.
     */
    public static LocalTime hawaiiLocalTimeNow() {
        return TimeService.hawaiiLocalTimeNow();
    }

    /**
     * Returns the local date and time in Hawaii
     *
     * @return a LocalDateTime now value in the "US/Pacific" zone.
     */
    public static LocalDateTime hawaiiLocalTimeDateNow() {
        return TimeService.hawaiiLocalTimeDateNow();
    }

    /**
     * Returns the UTC now time.
     *
     * @return a LocalTime in UTC.
     */
    public static LocalTime UTCTimeNow() {
        return TimeService.UTCTimeNow();
    }

    /**
     * Returns the UTC now date and time.
     *
     * @return a LocalDateTime in UTC.
     */
    public static LocalDateTime UTCDateTimeNow() {
        return TimeService.UTCDateTimeNow();
    }

    /**
     * Returns the TAI time now.
     *
     * @return a LocalTime object with TAI time.
     */
    public static LocalTime TAITimeNow() {
        return TimeService.TAITimeNow();
    }

    /**
     * Returns TAI as a data and time.
     *
     * @return a LocalDateTime object with TAI time.
     */
    public static LocalDateTime TAIDateTimeNow() {
        return TimeService.TAIDateTimeNow();
    }

    public static ZoneId ZoneIdOfTMTLocation() {
        return TimeService.ZoneIdOfTMTLocation();
    }
}

