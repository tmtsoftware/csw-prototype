package csw.services.alarms

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.util.Timeout
import csw.services.alarms.AlarmModel.SeverityLevel
import AlarmService._

import scala.concurrent.Await

/**
 * A convenience wrapper for [[AlarmService]] that blocks waiting for future return values.
 */
object BlockingAlarmService {
  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName      name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @return a new BlockingAlarmService instance
   */
  def apply(asName: String = defaultName)(implicit system: ActorSystem, timeout: Timeout): BlockingAlarmService = {
    val alarmService = Await.result(AlarmService(asName), timeout.duration)
    BlockingAlarmService(alarmService)
  }
}

/**
 * A convenience wrapper for [[AlarmService]] that blocks waiting for future return values.
 */
case class BlockingAlarmService(alarmService: AlarmService)(implicit val timeout: Timeout, context: ActorRefFactory) {

  /**
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @param severity the new value of the severity
   */
  def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): Unit =
    Await.result(alarmService.setSeverity(alarmKey, severity), timeout.duration)

}
