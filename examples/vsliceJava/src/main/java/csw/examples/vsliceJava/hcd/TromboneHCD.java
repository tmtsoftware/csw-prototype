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
import csw.services.loc.ComponentType;
import csw.services.pkg.Supervisor3;
import csw.util.config.*;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.Configurations.ConfigKey;
import csw.util.config.StateVariable.CurrentState;
import javacsw.services.cs.akka.JConfigServiceClient;
import javacsw.services.loc.JComponentType;
import javacsw.services.pkg.*;
import scala.PartialFunction;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JConfigDSL.*;
import static javacsw.util.config.JUnitsOfMeasure.encoder;
import static javacsw.services.pkg.JSupervisor3.*;

import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisStarted;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisStatistics;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisUpdate;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.InitialState;
import csw.examples.vsliceJava.hcd.SingleAxisSimulator.GetStatistics;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * TMT Source Code: 6/20/16.
 */
@SuppressWarnings({"unused", "CodeBlock2Expr", "OptionalUsedAsFieldOrParameterType"})
public class TromboneHCD extends JHcdControllerWithLifecycleHandler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  // Initialize axis from ConfigService
  AxisConfig axisConfig;

  // Create an axis for simulating trombone motion
  ActorRef tromboneAxis;

  // Initialize values -- This causes an update to the listener
  Timeout timeout = new Timeout(Duration.create(2, "seconds"));


  // The current axis position from the hardware axis, initialize to default value
  AxisUpdate current;
  AxisStatistics stats;

  // Keep track of the last SetupConfig to be received from external
  SetupConfig lastReceivedSC;

  final ActorRef supervisor;


  // Actor constructor: use the props() method to create the actor.
  private TromboneHCD(final HcdInfo info, ActorRef supervisor) throws Exception {
    super(info);

    this.supervisor = supervisor;

    // Note: The scala version initializes the following variables in a non-blocking way using a for comprehension.
    // It should be possible to do something similar in Java, but it would be more complicated.

    // Initialize axis from ConfigService
    axisConfig = getAxisConfig().get();

    // Create an axis for simulating trombone motion
    tromboneAxis = setupAxis(axisConfig);

    // The current axis position from the hardware axis, initialize to default value
    current = (AxisUpdate) Await.result(Patterns.ask(tromboneAxis, InitialState.instance, timeout), timeout.duration());
    stats = (AxisStatistics) Await.result(Patterns.ask(tromboneAxis, GetStatistics.instance, timeout), timeout.duration());

    // Keep track of the last SetupConfig to be received from external
    lastReceivedSC = SetupConfig(TromboneHCD.trombonePrefix);

    // --

    // Receive actor messages
    receive(initializingReceive());

    // Required setup for Lifecycle in order to get messages
    supervisor.tell(Initialized, self());
    supervisor.tell(Started, self());
  }

  PartialFunction<Object, BoxedUnit> initializingReceive() {
    return ReceiveBuilder
      .matchEquals(Running, e -> {
        // When Running is received, transition to running Receive
        context().become(runningReceive);
      })
      .matchAny(x -> log.warning("Unexpected message in TromboneHCD:initializingReceive: " + x))
      .build();
  }


  PartialFunction<Object, BoxedUnit> runningReceive1() {
    return ReceiveBuilder
      .matchEquals(TromboneEngineering.GetAxisStats, e -> {
        tromboneAxis.tell(GetStatistics.instance, self());
      })
      .match(AxisStarted.class, e -> {
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
      .match(AxisUpdate.class, e -> {
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
        notifySubscribers(tromboneAxisState);
      })
      .match(AxisStatistics.class, e -> {
        log.debug("AxisStatus: " + e);
        // Update actor statistics
        stats = e;
        CurrentState tromboneStats = jadd(defaultStatsState,
          jset(datumCountKey, e.initCount),
          jset(moveCountKey, e.moveCount),
          jset(limitCountKey, e.limitCount),
          jset(homeCountKey, e.homeCount),
          jset(successCountKey, e.successCount),
          jset(failureCountKey, e.failureCount),
          jset(cancelCountKey, e.cancelCount)
        );
        notifySubscribers(tromboneStats);
      })
      .matchEquals(Running, e -> {
        log.info("Received Running");
        context().become(runningReceive);
      })
      .matchEquals(RunningOffline, e -> {
        log.info("Received RunningOffline");
      })
      .matchEquals(DoRestart, e -> {
        log.info("Received DoRestart");
      })
      .matchEquals(DoShutdown, e -> {
        log.info("Received DoShutdown");
        // Just say complete for now
        supervisor.tell(ShutdownComplete, self());
      })
      .match(Supervisor3.LifecycleFailureInfo.class, e -> {
        log.info("Received failed state: " + e.state() + " for reason: " + e.reason());
      })
      .matchAny(x -> log.warning("Unexpected message in TromboneHCD:unhandledPF: " + x))
      .build();
  }

  PartialFunction<Object, BoxedUnit> runningReceive = defaultReceive().orElse(runningReceive1());

  /**
   * @param sc the config received
   */
  @Override
  public void process(SetupConfig sc) {
//    import TromboneHCD._

    log.debug("Trombone process received sc: " + sc);

    // Store the last received for diags
    lastReceivedSC = sc;

    ConfigKey configKey = sc.configKey();
    if (configKey.equals(axisMoveCK)) {
      tromboneAxis.tell(new SingleAxisSimulator.Move(jvalue(jitem(sc, positionKey)), true), self());
    } else if (configKey.equals(axisDatumCK)) {
      log.info("Received Datum");
      tromboneAxis.tell(SingleAxisSimulator.Datum.instance, self());
    } else if (configKey.equals(axisHomeCK)) {
      tromboneAxis.tell(SingleAxisSimulator.Home.instance, self());
    } else if (configKey.equals(axisCancelCK)) {
      tromboneAxis.tell(SingleAxisSimulator.CancelMove.instance, self());
    }
  }

  ActorRef setupAxis(AxisConfig ac) {
    return context().actorOf(SingleAxisSimulator.props(ac, Optional.of(self())), "Test1");
  }

  // -- Utility functions

  // Gets the trombone config file from the config service, or uses the trombone.conf resource file if that doesn't work
  private CompletableFuture<AxisConfig> getAxisConfig() {
    File tromboneConfigFile = new File("trombone/hcd/trombone.conf");
    File resource = new File("tromboneHCD.conf");

    //noinspection OptionalGetWithoutIsPresent
    return JConfigServiceClient.getConfigFromConfigService(tromboneConfigFile, Optional.empty(), Optional.of(resource), context().system(), timeout)
      .thenApply(config -> new AxisConfig(config.get()));
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
  public static final String componentName = "tromboneHCD";
  public static final ComponentType componentType = JComponentType.HCD;
  public static final String componentClassName = "csw.examples.vsliceJava.hcd.TromboneHCD";
  public static final String trombonePrefix = "nfiraos.ncc.tromboneHCD";

  public static final String tromboneAxisName = "tromboneAxis";

  public static final String axisStatePrefix = trombonePrefix + ".axis1State";
  public static final ConfigKey axisStateCK = new ConfigKey(axisStatePrefix);
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
  public static final ConfigKey axisStatsCK = new ConfigKey(axisStatsPrefix);
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
  public static final ConfigKey axisConfigCK = new ConfigKey(axisConfigPrefix);
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
  public static final ConfigKey axisMoveCK = new ConfigKey(axisMovePrefix);

  public static SetupConfig positionSC(int value) {
    return sc(axisMovePrefix, jset(positionKey, value).withUnits(encoder));
  }

  public static final String axisDatumPrefix = trombonePrefix + ".datum";
  public static final ConfigKey axisDatumCK = new ConfigKey(axisDatumPrefix);
  public static final SetupConfig datumSC = SetupConfig(axisDatumCK);

  public static final String axisHomePrefix = trombonePrefix + ".home";
  public static final ConfigKey axisHomeCK = new ConfigKey(axisHomePrefix);
  public static final SetupConfig homeSC = SetupConfig(axisHomeCK);

  public static final String axisCancelPrefix = trombonePrefix + ".cancel";
  public static final ConfigKey axisCancelCK = new ConfigKey(axisCancelPrefix);
  public static final SetupConfig cancelSC = SetupConfig(axisCancelCK);

  // Testing messages for TromboneHCD
  public enum TromboneEngineering {
    GetAxisStats,
    GetAxisConfig
  }
}





