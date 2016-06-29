package csw.services.alarms

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.alarms.AlarmModel.{AlarmStatus, SeverityLevel}

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
    * @param initialMap a map of alarm keys to the initial severity values (which can be updated later by sending a [[ChangeSeverity]] message)
    * @param publishIntervalSecs publish the alarm's severity every this number of seconds (default: less than the expire time)
    * @return the actorRef
    */
  def props(alarmService: AlarmService, initialMap: Map[AlarmKey, SeverityLevel],
            publishIntervalSecs: Int = max(AlarmService.defaultExpireSecs - 2, 1)): Props =
    Props(classOf[AlarmServiceSetSeverityActor], alarmService, initialMap, publishIntervalSecs)
}

private class AlarmServiceSetSeverityActor(alarmService: AlarmService, initialMap: Map[AlarmKey, SeverityLevel], publishIntervalSecs: Int)
  extends Actor with ActorLogging {

  import AlarmServiceSetSeverityActor._
  import context.dispatcher

  val delay = publishIntervalSecs.seconds
  context.system.scheduler.schedule(delay, delay, self, Publish)

  def receive: Receive = working(initialMap)

  def working(map: Map[AlarmKey, SeverityLevel]): Receive = {
    case ChangeSeverity(m) ⇒
      // XXX do immediate publish!
      context.become(working(map ++ m))

    case Publish ⇒
      for ((a, s) ← map) {
        alarmService.setSeverity(a, s)
      }

    case x ⇒ log.error(s"Received unexpected message: $x")
  }

}
