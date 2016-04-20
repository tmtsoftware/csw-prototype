package csw.services.ts

import java.time.Instant
import java.time.Clock
import java.time.ZoneId

/**
  * TODO: Work in progress...
  *
  * (Based on code contributed by Takashi Nakamoto)
  */
object TimeScale {

  sealed trait TimeScale

  /**
    * GPS, Global Positioning System time, is the atomic time scale implemented by the atomic clocks in the GPS ground
    * control stations and the GPS satellites themselves. GPS time was zero at 0h 6-Jan-1980 and since it is not
    * perturbed by leap seconds GPS is now ahead of UTC by 17 seconds.
    */
  case object GPS extends TimeScale

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
    * Local time is the date/time reported by your PC (as seen by your web browser). If your PC clock is accurate to
    * a second then the other time scales displayed above will also be accurate to within one second.
    */
  case object Java extends TimeScale


  case class TsInstant[A <: TimeScale](instant: Instant, timeScale: A) {
    override def toString = instant.toString + " in " + timeScale.toString
  }

  def toTAI[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[TAI.type]] = tsi match {
    case TsInstant(i, TAI) => Some(TsInstant(i, TAI))
    case TsInstant(i, GPS) => Some(TsInstant(i.plusSeconds(19), TAI))
    case _ => None
  }

  def toGPS[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[GPS.type]] = tsi match {
    case TsInstant(i, GPS) => Some(TsInstant(i, GPS))
    case TsInstant(i, TAI) => Some(TsInstant(i.minusSeconds(19), GPS))
    case TsInstant(i, UTC) => Some(TsInstant(i.plusSeconds(17), GPS))
    case _ => None
  }

  def toUTC[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[UTC.type]] = tsi match {
    case TsInstant(i, UTC) => Some(TsInstant(i, UTC))
    case TsInstant(i, TAI) => Some(TsInstant[UTC.type](i.minusSeconds(36), UTC)) // TODO: to be implemented correctly
    case TsInstant(i, GPS) => Some(TsInstant(i.minusSeconds(17), UTC)) // TODO: to be implemented correctly
    case _ => None
  }

  def toJava[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[Java.type]] = tsi match {
    case TsInstant(i, Java) => Some(TsInstant(i, Java))
    case _ => None
  }

  class TAIClock(zoneId: ZoneId) extends Clock {
    override def instant(): Instant = Instant.now().plusSeconds(36)

    // TODO: to be implemented correctly using PTP
    override def getZone: ZoneId = zoneId

    def taiInstant(): TsInstant[TAI.type] = new TsInstant(instant(), TAI)

    override def withZone(zoneId: ZoneId): Clock = new TAIClock(zoneId)
  }

  object TAIClock extends TAIClock(ZoneId.of("UTC")) {
    def clockWithZone(zoneId: ZoneId): TAIClock = new TAIClock(zoneId)
  }
}

object Main extends App {

  import TimeScale._

  val taii = TAIClock.taiInstant()
  println(taii)

  val gpsi = TimeScale.toGPS(taii).get
  println(gpsi)

  val utci = TimeScale.toUTC(gpsi).get
  println(utci)

  val hawaiiLocalTime = utci.instant.atZone(TimeService.ZoneIdOfTMTLocation)
  println(hawaiiLocalTime)

  val tokyoLocalTime = hawaiiLocalTime.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
  println(tokyoLocalTime)

  val nextTrigger = new TsInstant(taii.instant.plusSeconds(3600), TAI)
  // TimeServiceScheduler.scheduleOnce(nextTrigger, receiver, "wake up")
}
