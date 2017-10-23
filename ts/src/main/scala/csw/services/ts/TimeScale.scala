package csw.services.ts

import java.time.Instant
import java.time.Clock
import java.time.ZoneId

/**
 * TODO: Work in progress...
 * See https://confluence.qps.nl/display/KBE/UTC+to+GPS+Time+Correction for background information.
 *
 * (Based on code contributed by Takashi Nakamoto)
 */
object TimeScale {

  sealed trait TimeScale

  /**
   * TAI, Temps Atomique International, is the international atomic time scale based on a continuous counting of the
   * SI second. TAI is currently ahead of UTC by 36 seconds. TAI is always ahead of GPS by 19 seconds.
   */
  case object TAI extends TimeScale

  /**
   * UTC, Coordinated Universal Time, popularly known as GMT (Greenwich Mean Time), or Zulu time. Local time differs
   * from UTC by the number of hours of your timezone.
   */
  case object UTC extends TimeScale

  /**
   * Local time, the date/time reported by your PC. If your PC clock is accurate to
   * a second then the other time scales displayed above will also be accurate to within one second.
   */
  case object LOCAL extends TimeScale

  /**
   * An instant in the given time scale
   */
  case class TsInstant[A <: TimeScale](instant: Instant, timeScale: A) {
    override def toString = instant.toString + " in " + timeScale.toString
  }

  /**
   * Converts the given instant to TAI time
   */
  def toTAI[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[TAI.type]] = tsi match {
    case TsInstant(i, TAI) => Some(TsInstant(i, TAI))
    case TsInstant(i, UTC) => Some(TsInstant(i.plusSeconds(TimeService.UTCtoTAIoffset), TAI))
    case _                 => None
  }

  /**
   * Converts the given instant to UTC time
   */
  def toUTC[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[UTC.type]] = tsi match {
    case TsInstant(i, UTC) => Some(TsInstant(i, UTC))
    case TsInstant(i, TAI) => Some(TsInstant[UTC.type](i.minusSeconds(TimeService.UTCtoTAIoffset), UTC))
    case _                 => None
  }

  /**
   * Converts the given instant to LOCAL time
   */
  def toLocalTime[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[LOCAL.type]] = tsi match {
    case TsInstant(i, LOCAL) => Some(TsInstant(i, LOCAL))
    case _                   => None
  }

  class TAIClock(zoneId: ZoneId) extends Clock {
    override def instant(): Instant = Instant.now().plusSeconds(TimeService.UTCtoTAIoffset)

    override def getZone: ZoneId = zoneId

    def taiInstant(): TsInstant[TAI.type] = new TsInstant(instant(), TAI)

    override def withZone(zoneId: ZoneId): Clock = new TAIClock(zoneId)
  }

  object TAIClock extends TAIClock(ZoneId.of("UTC")) {
    def clockWithZone(zoneId: ZoneId): TAIClock = new TAIClock(zoneId)
  }
}

object Main extends App {

  // XXX TODO: add tests

  import TimeScale._

  val taii = TAIClock.taiInstant()
  println(taii)

  val utci = TimeScale.toUTC(taii).get
  println(utci)

  val hawaiiLocalTime = utci.instant.atZone(TimeService.ZoneIdOfTMTLocation)
  println(hawaiiLocalTime)

  val tokyoLocalTime = hawaiiLocalTime.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
  println(tokyoLocalTime)

  val nextTrigger = new TsInstant(taii.instant.plusSeconds(3700), TAI)
  // TimeServiceScheduler.scheduleOnce(nextTrigger, receiver, "wake up")
}
