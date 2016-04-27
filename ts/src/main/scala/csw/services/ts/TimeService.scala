package csw.services.ts

import java.time._

import akka.actor._
import akka.event.Logging

/**
 * TMT Prototype CSW Time Service
 * Note this requires Java 8
 */
object TimeService {

  // As of July 2015, GPS time is 17 seconds ahead of UTC
  // NOTE: Dependent on number of leap seconds so far and can not be calculated!
  // May need to update this value if another leap second is declared...
  private[ts] val UTCtoGPSoffset = 17

  // TAI − GPS = 19 seconds (constant)
  private[ts] val GPStoTAIoffset = 19

  // Offset from UTC to TAI
  private[ts] val UTCtoTAIoffset = UTCtoGPSoffset + GPStoTAIoffset

  // Java 8 clocks for the different needs
  private val utcClock = Clock.systemUTC()
  private val localClock = Clock.systemDefaultZone()
  private val hclock = Clock.system(ZoneId.of("US/Hawaii"))

  /**
   * Returns the local time in the current time zone.
   *
   * @return a LocalTime now value.
   */
  def localTimeNow: LocalTime = LocalTime.now(localClock)

  /**
   * Returns the local date and time in the current time zone.
   *
   * @return a LocalDateTime now value.
   */
  def localTimeDateNow: LocalDateTime = LocalDateTime.now(localClock)

  /**
   * Returns the local time now in Hawaii
   *
   * @return a LocalTime now value in the "US/Pacific" zone.
   */
  def hawaiiLocalTimeNow: LocalTime = LocalTime.now(hclock)

  /**
   * Returns the local date and time in Hawaii
   *
   * @return a LocalDateTime now value in the "US/Pacific" zone.
   */
  def hawaiiLocalTimeDateNow: LocalDateTime = LocalDateTime.now(hclock)

  /**
   * Returns the UTC now time.
   *
   * @return a LocalTime in UTC.
   */
  def UTCTimeNow: LocalTime = LocalTime.now(utcClock)

  /**
   * Returns the UTC now date and time.
   *
   * @return a LocalDateTime in UTC.
   */
  def UTCDateTimeNow: LocalDateTime = LocalDateTime.now(utcClock)

  /**
   * Returns the TAI time now.
   *
   * @return a LocalTime object with TAI time.
   */
  def TAITimeNow: LocalTime = UTCTimeNow.plusSeconds(UTCtoTAIoffset)

  /**
   * Returns TAI as a data and time.
   *
   * @return a LocalDateTime object with TAI time.
   */
  def TAIDateTimeNow: LocalDateTime = UTCDateTimeNow.plusSeconds(UTCtoTAIoffset)

  /**
   * The zone id corresponding to the TMT telescope location (TODO: find location for telescope)
   */
  val ZoneIdOfTMTLocation: ZoneId = ZoneId.of("US/Hawaii")

  /**
   * TimeServiceSchedule provides a component actor with timed messages
   * <p>
   * scheduleOnce -  sends a message to an actor once some time in the future
   * <p>
   * schedule    -  waits until a specific time and then sends periodic message to an actor until cancelled
   * <p>
   * Must extend an Actor with ActorLogging
   */
  trait TimeServiceScheduler {
    self: Actor with ActorLogging ⇒

    import scala.concurrent.duration.{ FiniteDuration, NANOSECONDS }

    implicit val ec = context.system.dispatcher

    // This converts java.time data into Akka FiniteDuration
    // Assumes startTime is in the future
    private def toStartDuration(startTime: LocalTime): FiniteDuration = {
      val now = LocalTime.now.toNanoOfDay
      val t1 = startTime.toNanoOfDay
      val futureTimeNano = t1 - now
      if (futureTimeNano < 0) {
        log.error(s"Requested schedule start time in not in the future: $futureTimeNano")
      }
      FiniteDuration(futureTimeNano, NANOSECONDS)
    }

    /**
     * Schedule a message to be sent once to an actor at a future time.
     * Uses Java 8 java.time types.
     *
     * @param startTime a LocalTime when the message should be sent
     * @param receiver  an actorRef for an actor that will receive the message
     * @param message   some message to be sent
     * @return a Cancellable that can be used to cancel the timer
     */
    def scheduleOnce(startTime: LocalTime, receiver: ActorRef, message: Any): Cancellable = {
      val startDuration = toStartDuration(startTime)
      // TODO need to handle errors from toStartDuration
      context.system.scheduler.scheduleOnce(startDuration, receiver, message)
    }

    /**
     * Schedule a message to be sent periodically to an actor starting at a future time. The scheduler must be
     * cancelled to stop the message.
     * Uses Java 8 java.time types.
     *
     * @param startTime a LocalTime when the first message should be sent
     * @param period    the Duration between messages
     * @param receiver  an actorRef for an actor that will receive the message
     * @param message   some message to be sent
     * @return a Cancellable that can be used to cancel the timer
     */
    def schedule(startTime: LocalTime, period: Duration, receiver: ActorRef, message: Any): Cancellable = {
      val startDuration = toStartDuration(startTime)
      // TODO need to handle errors from toStartDuration
      val schedulePeriod = FiniteDuration(period.toNanos, NANOSECONDS)
      context.system.scheduler.schedule(startDuration, schedulePeriod, receiver, message)
    }
  }
}

/**
  * A java friendly version of [[csw.services.ts.TimeService.TimeServiceScheduler]]
  */
abstract class JavaTimeServiceScheduler extends UntypedActor with ActorLogging with TimeService.TimeServiceScheduler


