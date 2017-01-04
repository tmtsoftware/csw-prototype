package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import csw.examples.vslice.hcd.TromboneHCD
import csw.examples.vslice.hcd.TromboneHCD._
import csw.services.alarms.AlarmModel.SeverityLevel.{Okay, Warning}
import csw.services.alarms.{AlarmKey, AlarmModel, AlarmService}
import csw.services.ccs.HcdController
import csw.util.config.StateVariable.CurrentState

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Actor manages Trombone alarms and provides the assembly interface to the TMT Alarm Service.
 *
 * Trombone has two alarms tied to reaching encoder limits, which would indicate abnormal behavior requiring an operator's attention.
 * The two alarms are highLimitAlarm and lowLimitAlarm indicating the axis has reached the high user limit and low user limit respectively.
 *
 * The monitor subscribes to CurrentState events from the HCD, which can be provided directly as a connection to the HCD or through a
 * CurrentStateReceiver actor, which uses the same message for subscribing.
 *
 * The AlarmMonitor has two states, one for normal operation with no alarms and one for when trombone is in an alarm state. This is possible
 * because the axis can't be in the high and low alarm state simultaneously. While in the normal state, the monitor looks for high or low
 * limits set in the CurrentState data from the HCD.  When in the alarm state, it looks for the alarm to return to the normal state so that
 * the alarm can be cleared. A CSW client must not only set its alarms when they go to an alarm state, but also clear them when the
 * conditions causing the alarm are removed.
 *
 * When the alarm starts up, it writes okay to the Alarm Service so that the Alarm Service client will begin to maintain the current
 * values for the Alarm Service, which requires that the Assembly update its alarms periodically. This is handled by the Alarm Service
 * client, and will cause the alarms to go to disconnected, if the assembly quits.
 *
 * @param currentStateReceiver the currentStateReceiver that delivers CurrentState messages either through an HCD or a CurrentStateReceiver.
 * @param alarmService reference to the alarm service used
 */
class TromboneAlarmMonitor(currentStateReceiver: ActorRef, alarmService: AlarmService) extends Actor with ActorLogging {
  import TromboneAlarmMonitor._
  import context.dispatcher

  //  implicit val timeout = Timeout(10.seconds)

  // Set the alarms to okay so that the Alarm Service client will update the alarms while this actor is alive
  sendLowLimitAlarm(alarmService, Okay)
  sendHighLimitAlarm(alarmService, Okay)
  context.become(monitorReceive(alarmService))

  // Subscribe this
  currentStateReceiver ! HcdController.Subscribe

  def receive: Receive = {
    case x => log.error(s"AlarmMonitor uninitialized receive received an unexpected message: $x")
  }

  /**
   * monitorReceive watches the CurrentState events for in low limit or in high limit set and sets
   * the alarms in the Alarm Service.
   *
   * @param alarmService the instance of AlarmService
   * @return the actor Receive partial function
   */
  def monitorReceive(alarmService: AlarmService): Receive = {
    case cs: CurrentState if cs.configKey == TromboneHCD.axisStateCK =>
      val inLowLimit = cs(inLowLimitKey)
      if (inLowLimit.head) {
        log.info(s"TromboneAssembly Alarm Monitor received a encoder low limit from the trombone HCD")
        sendLowLimitAlarm(alarmService, Warning)
        context.become(inAlarmStateReceive(alarmService, lowLimitAlarm))
      }
      val inHighLimit = cs(inHighLimitKey)
      if (inHighLimit.head) {
        log.info(s"TromboneAssembly Alarm Monitor received a encoder high limit from the trombone HCD")
        sendHighLimitAlarm(alarmService, Warning)
        context.become(inAlarmStateReceive(alarmService, highLimitAlarm))
      }

    case x => log.error(s"AlarmMonitor:monitorReceive received an unexpected message: $x")
  }

  def inAlarmStateReceive(alarmService: AlarmService, alarmKey: AlarmKey): Receive = {
    // One of our keys
    case cs: CurrentState if cs.configKey == TromboneHCD.axisStateCK =>
      alarmKey match {
        case `lowLimitAlarm` =>
          val inLowLimit = cs(inLowLimitKey)
          if (!inLowLimit.head) {
            log.info(s"TromboneAssembly Alarm Monitor low limit for the trombone HCD is cleared")
            sendLowLimitAlarm(alarmService, Okay)
            // Go back to monitor State once the alarm has been cleared
            context.become(monitorReceive(alarmService))
          }

        case `highLimitAlarm` =>
          val inHighLimit = cs(inHighLimitKey)
          if (!inHighLimit.head) {
            log.info(s"TromboneAssembly Alarm Monitor high limit for the trombone HCD is cleared")
            sendHighLimitAlarm(alarmService, Okay)
            // Go back to monitor State
            context.become(monitorReceive(alarmService))
          }
      }

    case x => log.error(s"TromboneAlarmMonitor:inAlarmStateReceive received an unexpected message: $x")
  }

  /**
   * Send the low limit severity to the Alarm Service
   *
   * @param alarmService the instance of the Alarm Service
   * @param severity     the severity that is used to set the lowLimitAlarm
   */
  private def sendLowLimitAlarm(alarmService: AlarmService, severity: AlarmModel.SeverityLevel): Unit = {
    val f = alarmService.setSeverity(lowLimitAlarm, severity)
    f.onComplete {
      case Failure(ex) => log.error(s"TromboneAlarmMonitor failed to set $lowLimitAlarm to $severity: $ex")
      case Success(s)  => log.info(s"TromboneAlarmMonitor successfully posted: $severity to the low limit alarm")
    }
    Await.ready(f, 3.seconds)
  }

  /**
   * Send the high limit severity to the Alarm Service
   *
   * @param alarmService the instance of the Alarm Service
   * @param severity     the severity that is used to set the highLimitAlarm
   */
  private def sendHighLimitAlarm(alarmService: AlarmService, severity: AlarmModel.SeverityLevel): Unit = {
    val f = alarmService.setSeverity(highLimitAlarm, severity)
    f.onComplete {
      case Failure(ex) => log.error(s"TromboneAlarmMonitor failed to set $highLimitAlarm to: $severity: $ex")
      case Success(s)  => log.info(s"TromboneAlarmMonitor successfully posted: $severity to the high limit alarm")
    }
    Await.ready(f, 3.seconds)
  }

}

object TromboneAlarmMonitor {

  def props(currentStateReceive: ActorRef, alarmService: AlarmService): Props = Props(classOf[TromboneAlarmMonitor], currentStateReceive, alarmService)

  // The alarm keys for the low and high trombone encoder limits
  val highLimitAlarm = AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisHighLimitAlarm")
  val lowLimitAlarm = AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisLowLimitAlarm")
}
