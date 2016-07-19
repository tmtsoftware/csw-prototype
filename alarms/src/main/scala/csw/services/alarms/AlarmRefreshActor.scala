package csw.services.alarms

import akka.actor.{Actor, ActorLogging, Props}
import csw.services.alarms.AlarmModel.SeverityLevel

import scala.concurrent.duration._

/**
 * An actor that continuously sets the severity of an alarm to a given value,
 * to keep the value from expiring in the database
 */
object AlarmRefreshActor {

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
   * @return the actorRef
   */
  def props(alarmService: AlarmService, initialMap: Map[AlarmKey, SeverityLevel]): Props = {
    Props(classOf[AlarmRefreshActor], alarmService, initialMap)
  }
}

private class AlarmRefreshActor(alarmService: AlarmService, initialMap: Map[AlarmKey, SeverityLevel])
    extends Actor with ActorLogging {

  import AlarmRefreshActor._
  import context.dispatcher

  val delay = alarmService.refreshSecs.seconds
  context.system.scheduler.schedule(delay, delay, self, Publish)

  def receive: Receive = working(initialMap)

  def working(map: Map[AlarmKey, SeverityLevel]): Receive = {
    case ChangeSeverity(m) =>
      context.become(working(map ++ m))

    case Publish =>
      for ((a, s) <- map) {
        alarmService.setSeverity(a, s)
      }

    case x => log.error(s"Received unexpected message: $x")
  }

}
