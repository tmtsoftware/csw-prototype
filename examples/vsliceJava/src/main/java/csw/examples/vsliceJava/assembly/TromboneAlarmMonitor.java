package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.alarms.AlarmKey;
import csw.services.alarms.AlarmModel;
import csw.util.config.JavaHelpers;
import javacsw.services.alarms.IAlarmService;
import javacsw.services.ccs.JHcdController;
import scala.PartialFunction;
import scala.Unit;
import scala.runtime.BoxedUnit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static csw.examples.vsliceJava.hcd.TromboneHCD.inHighLimitKey;
import static csw.examples.vsliceJava.hcd.TromboneHCD.inLowLimitKey;
import static csw.util.config.StateVariable.CurrentState;
import static javacsw.services.alarms.JAlarmModel.JSeverityLevel.Okay;
import static javacsw.services.alarms.JAlarmModel.JSeverityLevel.Warning;

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
 * because the axis can't be in the high and low alarm state simulatenously. While in the normal state, the monitor looks for high or low
 * limits set in the CurrentState data from the HCD.  When in the alarm state, it looks for the alarm to return to the normal state so that
 * the alarm can be cleared. A CSW client must not only set its alarms when they go to an alarm state, but also clear them when the
 * conditions causing the alarm are removed.
 *
 * When the alarm starts up, it writes okay to the Alarm Service so that the Alarm Service client will begin to maintain the current
 * values for the Alarm Service, which requires that the Assembly update its alarms periodically. This is handled by the Alarm Service
 * client, and will cause the alarms to go to disconnected, if the assembly quits.
 */
@SuppressWarnings("unused")
public class TromboneAlarmMonitor extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  /**
   * Constructor
   *
   * @param currentStateReceiver the currentStateReceiver that delivers CurrentState messages either through an HCD or a CurrentStateReceiver.
   */
  private TromboneAlarmMonitor(ActorRef currentStateReceiver, IAlarmService alarmService) {

    // Set the alarms to okay so that the Alarm Service client will update the alarms while this actor is alive
    sendLowLimitAlarm(alarmService, Okay);
    sendHighLimitAlarm(alarmService, Okay);

    // Subscribe this
    currentStateReceiver.tell(JHcdController.Subscribe, self());

    receive(monitorReceive(alarmService));
  }

  /**
   * monitorReceive watches the CurrentState events for in low limit or in high limit set and sets
   * the alarms in the Alarm Service.
   *
   * @param alarmService the instance of AlarmService
   * @return the actor Receive partial function
   */
    private PartialFunction<Object, BoxedUnit> monitorReceive(IAlarmService alarmService) {
      return ReceiveBuilder.
        match(CurrentState.class, cs -> {
          if (cs.configKey().equals(TromboneHCD.axisStateCK)) {
            boolean inLowLimit = JavaHelpers.jvalue(cs, inLowLimitKey);
            if (inLowLimit) {
              log.info("TromboneAssembly Alarm Monitor received a encoder low limit from the trombone HCD");
              sendLowLimitAlarm(alarmService, Warning);
              context().become(inAlarmStateReceive(alarmService, lowLimitAlarm));
            }
            boolean inHighLimit = JavaHelpers.jvalue(cs, inHighLimitKey);
            if (inHighLimit) {
              log.info("TromboneAssembly Alarm Monitor received a encoder high limit from the trombone HCD");
              sendHighLimitAlarm(alarmService, Warning);
              context().become(inAlarmStateReceive(alarmService, highLimitAlarm));
            }
          } else log.warning("AlarmMonitor:monitorReceive received an unexpected message: " + cs);
        }).
        matchAny(t -> log.warning("AlarmMonitor:monitorReceive received an unexpected message: " + t)).
        build();
  }

  private PartialFunction<Object, BoxedUnit> inAlarmStateReceive(IAlarmService alarmService, AlarmKey alarmKey) {
    return ReceiveBuilder.
      match(CurrentState.class, cs -> {
        if (cs.configKey().equals(TromboneHCD.axisStateCK)) {
          if (alarmKey.equals(lowLimitAlarm)) {
            boolean inLowLimit = JavaHelpers.jvalue(cs, inLowLimitKey);
            if (!inLowLimit) {
              log.info("TromboneAssembly Alarm Monitor low limit for the trombone HCD is cleared");
              sendLowLimitAlarm(alarmService, Okay);
              // Go back to monitor State once the alarm has been cleared
              context().become(monitorReceive(alarmService));
            }

          } else if (alarmKey.equals(highLimitAlarm)) {
            boolean inHighLimit = JavaHelpers.jvalue(cs, inHighLimitKey);
            if (!inHighLimit) {
              log.info("TromboneAssembly Alarm Monitor high limit for the trombone HCD is cleared");
              sendHighLimitAlarm(alarmService, Okay);
              // Go back to monitor State
              context().become(monitorReceive(alarmService));
            }
          }

        } else log.warning("TromboneAlarmMonitor:inAlarmStateReceive received an unexpected message: " + cs);
      }).
      matchAny(t -> log.warning("TromboneAlarmMonitor:inAlarmStateReceive received an unexpected message: " + t)).
      build();
  }

  /**
   * Send the low limit severity to the Alarm Service
   * @param alarmService the instance of the Alarm Service
   * @param severity the severity that is used to set the lowLimitAlarm
   */
  private void sendLowLimitAlarm(IAlarmService alarmService, AlarmModel.SeverityLevel severity) {
    CompletableFuture<Unit> f = alarmService.setSeverity(lowLimitAlarm, severity);
    f.exceptionally(ex -> {
      log.error("TromboneAlarmMonitor failed to set " + lowLimitAlarm + " to " + severity + ": " + ex);
      return null;
    }).thenAccept(t -> log.info("TromboneAlarmMonitor successfully posted: $severity to the low limit alarm"));
    try {
      f.get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error(e, "TromboneAlarmMonitor failed");
    }
  }

  /**
   * Send the high limit severity to the Alarm Service
   * @param alarmService the instance of the Alarm Service
   * @param severity the severity that is used to set the highLimitAlarm
   */
  private void sendHighLimitAlarm(IAlarmService alarmService, AlarmModel.SeverityLevel severity) {
    CompletableFuture<Unit> f = alarmService.setSeverity(highLimitAlarm, severity);
    f.exceptionally(ex -> {
      log.error("TromboneAlarmMonitor failed to set " + highLimitAlarm + " to " + severity + ": " + ex);
      return null;
    }).thenAccept(t -> log.info("TromboneAlarmMonitor successfully posted: $severity to the low limit alarm"));
    try {
      f.get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error(e, "TromboneAlarmMonitor failed");
    }
  }

  // --- static defs ---

  public static Props props(ActorRef currentStateReceiver, IAlarmService alarmService) {
    return Props.create(new Creator<TromboneAlarmMonitor>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneAlarmMonitor create() throws Exception {
        return new TromboneAlarmMonitor(currentStateReceiver, alarmService);
      }
    });
  }

  // The alarm keys for the low and high trombone encoder limits
   static final AlarmKey highLimitAlarm = new AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisHighLimitAlarm");
   static final AlarmKey lowLimitAlarm = new AlarmKey("nfiraos", "nfiraos.cc.trombone", "tromboneAxisLowLimitAlarm");

  private static final Timeout timeout = new Timeout(10, TimeUnit.SECONDS);
}


