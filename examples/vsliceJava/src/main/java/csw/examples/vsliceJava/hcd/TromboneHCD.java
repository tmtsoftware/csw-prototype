package csw.examples.vsliceJava.hcd;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.Config;
import csw.examples.vsliceJava.shared.TromboneData;
import csw.services.loc.ComponentType;
import csw.services.loc.LocationService;
import csw.services.pkg.ContainerComponent;
import csw.services.pkg.LifecycleManager;
import csw.services.pkg.Supervisor;
import csw.util.config.*;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Configurations.ConfigKey;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.loc.JComponentType;
import javacsw.services.loc.JLocationService;
import javacsw.services.pkg.*;
import javacsw.util.config.JUnitsOfMeasure;
import scala.PartialFunction;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;

import static javacsw.services.loc.JConnectionType.AkkaType;
import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JConfigDSL.*;
import static javacsw.util.config.JUnitsOfMeasure.encoder;

import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisStatistics;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisUpdate;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisConfig;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.InitialState;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.GetStatistics;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * TMT Source Code: 6/20/16.
 */
@SuppressWarnings("unused")
public class TromboneHCD extends JHcdControllerWithLifecycleHandler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  // Actor constructor: use the props() method to create the actor.
  private TromboneHCD(final HcdInfo info, ActorRef supervisor) throws Exception {
    super(info);

    // Receive actor messages
    receive(initializingReceive());

    // Required setup for Lifecycle in order to get messages
    // XXX TODO: Do we need to send Initialize and Startup?
    Supervisor.lifecycle(supervisor(), JLifecycleManager.Initialize);
    Supervisor.lifecycle(supervisor(), JLifecycleManager.Startup);
  }

  // Initialize axis from ConfigService
  AxisConfig axisConfig = getAxisConfig();

  // Create an axis for simulating trombone motion
  ActorRef tromboneAxis = setupAxis(axisConfig);

  // Initialize values -- This causes an update to the listener
  Timeout timeout = new Timeout(Duration.create(2, "seconds"));

  // The current axis position from the hardware axis, initialize to default value
  // (XXX TODO FIXME: Do we need to block here?)
  AxisUpdate current = (AxisUpdate) Await.result(Patterns.ask(tromboneAxis, InitialState.instance, timeout), timeout.duration());
  AxisStatistics stats = (AxisStatistics) Await.result(Patterns.ask(tromboneAxis, GetStatistics.instance, timeout), timeout.duration());

  // Keep track of the last SetupConfig to be received from external
  SetupConfig lastReceivedSC = SetupConfig(TromboneHCD.trombonePrefix);

  // This receive is used when executing a Home command
  PartialFunction<Object, BoxedUnit> initializingReceive() {
    return ReceiveBuilder
      .match(LifecycleManager.Running.class, e -> {
        // When Running is received, transition to running Receive
        context().become(runningReceive());
      })
      .matchAny(x -> log.warning("Unexpected message in TromboneHCD:initializingReceive: " + x))
      .build();
  }

  // Idea syntax checking makes orElse orElse a syntax error though it isn't, but this makes it go away
  PartialFunction<Object, BoxedUnit> runningReceive = defaultReceive().orElse(runningReceive1);

  PartialFunction<Object, BoxedUnit> runningReceive1 = runningReceivePF.orElse(runningReceive2);

  PartialFunction<Object, BoxedUnit> runningReceive2 = lifecycleReceivePF.orElse(unhandledPF);

  PartialFunction<Object, BoxedUnit> runningReceivePF() {
    return ReceiveBuilder
      .matchEquals(TromboneEngineering.GetAxisStats, e -> {
        tromboneAxis.tell(GetStatistics.instance, self());
      })
      .match(SingleAxisSimulator.AxisStarted.class, e -> {
        // println("Axis Started")
      })
      .matchEquals(TromboneEngineering.GetAxisConfig, e -> {
        CurrentState axisConfigState = jadd(defaultConfigState,
          jset(lowLimitKey, axisConfig.lowLimit),
          jset(lowUserKey, axisConfig.lowUser),
          jset(highUserKey, axisConfig.highUser),
          jset(highLimitKey, axisConfig.highLimit),
          jset(homeValueKey, axisConfig.home),
          jset(startValueKey, axisConfig.startPosition),
          jset(stepDelayMSKey, axisConfig.stepDelayMS)
        );
        notifySubscribers(axisConfigState);
      })
      .match(SingleAxisSimulator.AxisUpdate.class, e -> {
        //log.info(s"Axis Update: $au")
        // Update actor state
        current = e;
        CurrentState tromboneAxisState = jadd(defaultAxisState,
          jset(positionKey, e.current).withUnits(encoder),
          jset(stateKey, Choice(e.state.toString())),
          jset(inLowLimitKey, e.inLowLimit),
          jset(inHighLimitKey, e.inHighLimit),
          jset(inHomeKey, e.inHomed)
        );
        notifySubscribers(tromboneAxisState)
      })
      .matchAny(x -> log.warning("Unexpected message in TromboneHCD:initializingReceive: " + x))
      .build();
  }

//    case au@AxisUpdate(_, axisState, currentPosition, inLowLimit, inHighLimit, inHomed) =>
//      //log.info(s"Axis Update: $au")
//      // Update actor state
//      current = au
//      val tromboneAxisState = defaultAxisState.madd(
//        positionKey -> currentPosition withUnits encoder,
//        stateKey -> axisState.toString,
//        inLowLimitKey -> inLowLimit,
//        inHighLimitKey -> inHighLimit,
//        inHomeKey -> inHomed
//      )
//      notifySubscribers(tromboneAxisState)
//
//    case as: AxisStatistics =>
//      log.debug(s"AxisStatus: $as")
//      // Update actor statistics
//      stats = as
//      val tromboneStats = defaultStatsState.madd(
//        datumCountKey -> as.initCount,
//        moveCountKey -> as.moveCount,
//        limitCountKey -> as.limitCount,
//        homeCountKey -> as.homeCount,
//        successCountKey -> as.successCount,
//        failureCountKey -> as.failureCount,
//        cancelCountKey -> as.cancelCount
//      )
//      notifySubscribers(tromboneStats)
//  }

//  def lifecycleReceivePF: Receive = {
//    case Running =>
//      log.info("Received running")
//      context.become(runningReceive)
//    case RunningOffline =>
//      log.info("Received running offline")
//    case DoRestart =>
//      log.info("Received dorestart")
//    case DoShutdown =>
//      log.info("Received doshutdown")
//      // Just say complete for now
//      supervisor ! ShutdownComplete
//    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
//      log.info(s"Received failed state: $state for reason: $reason")
//  }
//
//  def unhandledPF: Receive = {
//    case x => log.error(s"Unexpected message in TromboneHCD:unhandledPF: $x")
//  }

  /**
   * @param sc the config received
   */
  @Override
  void process(SetupConfig sc) {
//    import TromboneHCD._

    log.debug("Trombone process received sc: " + sc);

    // Store the last received for diags
    lastReceivedSC = sc;

    sc.configKey match {
      case `axisMoveCK` =>
      tromboneAxis !Move(sc(positionKey).head, diagFlag = true)
      case `axisDatumCK` =>
      log.info("Received Datum")
      tromboneAxis !Datum
      case `axisHomeCK` =>
      tromboneAxis !Home
      case `axisCancelCK` =>
      tromboneAxis !CancelMove
    }
  }

  ActorRef setupAxis(AxisConfig ac) {
    return context().actorOf(SingleAxisSimulator.props(ac, Optional.of(self())), "Test1");
  }

  // Utility functions
  AxisConfig getAxisConfig() {
    // Will be obtained from the
    String name = getConfigString("axis-config.axisName");
    int lowLimit = getConfigInt("axis-config.lowLimit");
    int lowUser = getConfigInt("axis-config.lowUser");
    int highUser = getConfigInt("axis-config.highUser");
    int highLimit = getConfigInt("axis-config.highLimit");
    int home = getConfigInt("axis-config.home");
    int startPosition = getConfigInt("axis-config.startPosition");
    int stepDelayMS = getConfigInt("axis-config.stepDelayMS");
    return new AxisConfig(name, lowLimit, lowUser, highUser, highLimit, home, startPosition, stepDelayMS);
  }

  String getConfigString(String name) {
    return context().system().settings().config().getString("csw.examples.Trombone.hcd." + name);
  }

  int getConfigInt(String name) {
    return context().system().settings().config().getInt("csw.examples.Trombone.hcd." + name);
  }

  // --- Static defs ---

  /**
   * Used to create the TromboneHCD actor
   *
   * @param info       the HCD's prefix, used in configurations
   * @param supervisor the supervisor for the HCD
   * @return the Props needed to create the actor
   */
  public static Props props(final HcdInfo info, ActorRef supervisor) {
    return Props.create(new Creator<TromboneHCD>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneHCD create() throws Exception {
        return new TromboneHCD(info, supervisor);
      }
    });
  }

  // HCD Info
  public static final String componentName = "lgsTromboneHCD";
  public static final ComponentType componentType = JComponentType.HCD;
  public static final String componentClassName = "csw.examples.vslice.hcd.TromboneHCD";
  public static final String trombonePrefix = "nfiraos.ncc.tromboneHCD";

  public static final String tromboneAxisName = "tromboneAxis";

  public static final String axisStatePrefix = trombonePrefix + ".axis1State";
  public static final Configurations.ConfigKey axisStateCK = Configurations.ConfigKey.stringToConfigKey(axisStatePrefix);
  public static final StringKey axisNameKey = new StringKey("axisName");
  public static final Choice AXIS_IDLE = new Choice(AxisState.AXIS_IDLE.toString());
  public static final Choice AXIS_MOVING = new Choice(AxisState.AXIS_MOVING.toString());
  public static final Choice AXIS_ERROR = new Choice(AxisState.AXIS_ERROR.toString());
  public static final ChoiceKey stateKey = new ChoiceKey("axisState",
    Choices.from(AXIS_IDLE.toString(), AXIS_MOVING.toString(), AXIS_ERROR.toString()));
  public static final IntKey positionKey = new IntKey("position");
  public static final BooleanKey inLowLimitKey = new BooleanKey("lowLimit");
  public static final BooleanKey inHighLimitKey = new BooleanKey("highLimit");
  public static final BooleanKey inHomeKey = new BooleanKey("homed");

  public static final CurrentState defaultAxisState = cs(axisStatePrefix,
    jset(axisNameKey, tromboneAxisName),
    jset(stateKey, AXIS_IDLE),
    jset(positionKey, 0).withUnits(encoder),
    jset(inLowLimitKey, false),
    jset(inHighLimitKey, false),
    jset(inHomeKey, false));

  public static final String axisStatsPrefix = trombonePrefix + ".axisStats";
  public static final ConfigKey axisStatsCK = ConfigKey.stringToConfigKey(axisStatsPrefix);
  public static final IntKey datumCountKey = IntKey("initCount");
  public static final IntKey moveCountKey = IntKey("moveCount");
  public static final IntKey homeCountKey = IntKey("homeCount");
  public static final IntKey limitCountKey = IntKey("limitCount");
  public static final IntKey successCountKey = IntKey("successCount");
  public static final IntKey failureCountKey = IntKey("failureCount");
  public static final IntKey cancelCountKey = IntKey("cancelCount");
  public static final CurrentState defaultStatsState = cs(axisStatsPrefix,
    jset(axisNameKey, tromboneAxisName),
    jset(datumCountKey, 0),
    jset(moveCountKey, 0),
    jset(homeCountKey, 0),
    jset(limitCountKey, 0),
    jset(successCountKey, 0),
    jset(failureCountKey, 0),
    jset(cancelCountKey, 0));

  public static final String axisConfigPrefix = trombonePrefix + ".axisConfig";
  public static final ConfigKey axisConfigCK = ConfigKey.stringToConfigKey(axisConfigPrefix);
  // axisNameKey
  public static final IntKey lowLimitKey = IntKey("lowLimit");
  public static final IntKey lowUserKey = IntKey("lowUser");
  public static final IntKey highUserKey = IntKey("highUser");
  public static final IntKey highLimitKey = IntKey("highLimit");
  public static final IntKey homeValueKey = IntKey("homeValue");
  public static final IntKey startValueKey = IntKey("startValue");
  public static final IntKey stepDelayMSKey = IntKey("stepDelayMS");
  // No full default current state because it is determined at runtime
  public static final CurrentState defaultConfigState = cs(axisConfigPrefix,
    jset(axisNameKey, tromboneAxisName)
  );

  public static final String axisMovePrefix = trombonePrefix + ".move";
  public static final ConfigKey axisMoveCK = ConfigKey.stringToConfigKey(axisMovePrefix);

  public static SetupConfig positionSC(int value) {
    return sc(axisMovePrefix, jset(positionKey, value).withUnits(encoder));
  }

  public static final String axisDatumPrefix = trombonePrefix + ".datum";
  public static final ConfigKey axisDatumCK = ConfigKey.stringToConfigKey(axisDatumPrefix);
  public static final SetupConfig datumSC = SetupConfig(axisDatumCK);

  public static final String axisHomePrefix = trombonePrefix + ".home";
  public static final ConfigKey axisHomeCK = ConfigKey.stringToConfigKey(axisHomePrefix);
  public static final SetupConfig homeSC = SetupConfig(axisHomeCK);

  public static final String axisCancelPrefix = trombonePrefix + ".cancel";
  public static final ConfigKey axisCancelCK = ConfigKey.stringToConfigKey(axisCancelPrefix);
  public static final SetupConfig cancelSC = SetupConfig(axisCancelCK);

  // Testing messages for TromboneHCD
  enum TromboneEngineering {
    GetAxisStats,
    GetAxisConfig
  }

  /**
   * Starts Assembly as a standalone application.
   */
  public static void main(String[] argv) {

    Config setup = ContainerComponent.parseStringConfig(TromboneData.testConf);
    Config componentConf = setup.getConfig("container.components." + componentName);

    HcdInfo testInfo = JComponentSup.hcdInfo(componentName, trombonePrefix, componentClassName, JComponent.DoNotRegister,
      Collections.singleton(AkkaType), FiniteDuration.create(1, "second"));
    HcdInfo hcdInfo = JContainerComponent.parseHcd(componentName, componentConf).orElse(testInfo);

    LocationService.initInterface();

    System.out.println("Starting TromboneHCD: " + hcdInfo);

    Supervisor.create(hcdInfo);
  }
}





