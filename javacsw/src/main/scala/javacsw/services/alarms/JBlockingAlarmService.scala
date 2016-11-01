package javacsw.services.alarms

import akka.actor.ActorRefFactory
import akka.util.Timeout
import csw.services.alarms.AlarmModel.SeverityLevel
import csw.services.alarms.{AlarmKey, AlarmService}

import scala.concurrent.Await

/**
 * Support for blocking Java API for the Alarm Service
 */
private[alarms] object JBlockingAlarmService {
  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName  name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param system  the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new IBlockingAlarmService instance
   */
  def lookup(asName: String, system: ActorRefFactory, timeout: Timeout): IBlockingAlarmService = {
    import system.dispatcher
    implicit val sys = system
    implicit val t = timeout
    Await.result(AlarmService(asName).map(JBlockingAlarmService(_, timeout, sys).asInstanceOf[IBlockingAlarmService]), timeout.duration)
  }
}

/**
 * Java API Implementation for the Alarm Service
 */
case class JBlockingAlarmService(alarmService: AlarmService, timeout: Timeout, system: ActorRefFactory) extends IBlockingAlarmService {

  import system.dispatcher

  override def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): Unit =
    Await.result(alarmService.setSeverity(alarmKey, severity), timeout.duration)

}

