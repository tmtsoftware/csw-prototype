package csw.services.alarms

import akka.actor.{Actor, ActorLogging, Props}
import csw.services.alarms.AlarmModel.SeverityLevel

import math._
import scala.concurrent.duration._

/**
 * An actor that continuously sets the severity of an alarm to a given value,
 * to keep the value from expiring in the database
 */
object AlarmServiceSetSeverityActor {

  /**
   * Actor message to change the severity level of selected alarms (only the ones contained in the map)
   *
   * @param alarms a map of alarm key to the severity level that it should now have
   */
  case class ChangeSeverity(alarms: Map[AlarmKey, SeverityLevel])

  /**
   * Message sent by timer to tell actor to republish the alarm severities
   */
  case object Publish

  /**
   * Used to start the actor.
   *
   * @param alarmService Alarm Service to use to set the severity
   * @param initialMap a map of alarm keys to the initial severity values (which can be updated later by sending a ChangeSeverity message)
   * @param expireSecs the amount of time in seconds before the alarm severity expires (defaults to 15 sec)
   * @return the actorRef
   */
  def props(alarmService: AlarmService, initialMap: Map[AlarmKey, SeverityLevel], expireSecs: Int = AlarmService.defaultExpireSecs): Props =
    Props(classOf[AlarmServiceSetSeverityActor], alarmService, initialMap, expireSecs)
}

private class AlarmServiceSetSeverityActor(alarmService: AlarmService, initialMap: Map[AlarmKey, SeverityLevel], expireSecs: Int)
    extends Actor with ActorLogging {

  import AlarmServiceSetSeverityActor._
  import context.dispatcher

  // Update the key 5 seconds before it expires
  val delay = max(expireSecs - 5, 1).seconds
  context.system.scheduler.schedule(delay, delay, self, Publish)

  def receive: Receive = working(initialMap)

  def working(map: Map[AlarmKey, SeverityLevel]): Receive = {
    case ChangeSeverity(m) ⇒
      context.become(working(map ++ m))

    case Publish ⇒
      for ((a, s) ← map) {
        alarmService.setSeverity(a, s, expireSecs)
      }

    case x ⇒ log.error(s"Received unexpected message: $x")
  }

}
