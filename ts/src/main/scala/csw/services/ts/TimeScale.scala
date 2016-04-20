package csw.services.ts

import java.time.Instant
import java.time.Clock
import java.time.ZoneId

/**
  * @author Takashi Nakamoto
  */
object TimeScale {
  sealed trait TimeScale
  case object GPS extends TimeScale
  case object TAI extends TimeScale
  case object UTC extends TimeScale
  case object Java extends TimeScale

  case class TsInstant[A <: TimeScale](instant: Instant, timeScale: A) {
    override def toString = instant.toString + " in " + timeScale.toString
  }

  object OfTimeScale {
    def unapply[A <: TimeScale](tsi: TsInstant[A]): Option[TimeScale] = Some(tsi.timeScale)
  }

  def toTAI[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[TAI.type]] = tsi match {
    case OfTimeScale(TAI) => tsi match {
      case TsInstant(i, TAI) => Some(TsInstant(i, TAI))
      case _ => None
    }
    case OfTimeScale(GPS) => tsi match {
      case TsInstant(i, GPS) => Some(TsInstant(i.plusSeconds(19), TAI))
      case _ => None
    }
    case _ => None
  }

  def toGPS[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[GPS.type]] = tsi match {
    case OfTimeScale(GPS) => tsi match {
      case TsInstant(i, GPS) => Some(TsInstant(i, GPS))
      case _ => None
    }
    case OfTimeScale(TAI) => tsi match {
      case TsInstant(i, TAI) => Some(TsInstant(i.minusSeconds(19), GPS))
      case _ => None
    }
    case _ => None
  }

  def toUTC[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[UTC.type]] = tsi match {
    case OfTimeScale(UTC) => tsi match {
      case TsInstant(i, UTC) => Some(TsInstant(i, UTC))
      case _ => None
    }
    case OfTimeScale(TAI) => tsi match {
      case TsInstant(i, TAI) => Some(TsInstant[UTC.type](i.minusSeconds(36), UTC)) // to be implemented correctly
      case _ => None
    }
    case OfTimeScale(GPS) => tsi match {
      case TsInstant(i, GPS) => Some(TsInstant(i.minusSeconds(17), UTC)) // to be implemented correctly
      case _ => None
    }
    case _ => None
  }

  def toJava[A <: TimeScale](tsi: TsInstant[A]): Option[TsInstant[Java.type]] = tsi match {
    case OfTimeScale(Java) => tsi match {
      case TsInstant(i, Java) => Some(TsInstant(i, Java))
      case _ => None
    }
    case OfTimeScale(TAI) => None // to be implemented
    case OfTimeScale(GPS) => None // to be implemented
    case _ => None
  }

  class TAIClock(zoneId: ZoneId) extends Clock {
    override def instant(): Instant = Instant.now().plusSeconds(36) // to be implemented correctly using PTP
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
