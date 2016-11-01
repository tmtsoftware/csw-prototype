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
   * Actor message to set the severity level of selected alarms (only the ones contained in the map)
   *
   * @param alarms a map of alarm key to the severity level that it should now have
   */
  case class SetSeverity(alarms: Map[AlarmKey, SeverityLevel], setNow: Boolean)

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

  val delay = alarmService.asInstanceOf[AlarmServiceImpl].refreshSecs.seconds
  context.system.scheduler.schedule(Duration.Zero, delay, self, Publish)

  def receive: Receive = working(initialMap)

  def working(map: Map[AlarmKey, SeverityLevel]): Receive = {
    case SetSeverity(m, setNow) =>
      context.become(working(map ++ m))
      if (setNow) {
        for ((a, s) <- m) {
          alarmService.setSeverity(a, s)
        }
      }

    case Publish =>
      for ((a, s) <- map) {
        alarmService.setSeverity(a, s)
      }

    case x => log.error(s"Received unexpected message: $x")
  }

}
