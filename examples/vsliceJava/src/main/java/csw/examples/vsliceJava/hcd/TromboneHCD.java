package csw.examples.vsliceJava.hcd;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.loc.ComponentType;
import csw.services.pkg.Supervisor;
import csw.util.config.*;
import javacsw.services.loc.JComponentType;
import javacsw.services.pkg.JHcdControllerWithLifecycleHandler;
import javacsw.services.pkg.JLifecycleManager;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState;


/**
 * TMT Source Code: 6/20/16.
 */
public class TromboneHCD extends JHcdControllerWithLifecycleHandler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  // Actor constructor: use the props() method to create the actor.
  private TromboneHCD(final HcdInfo info, ActorRef supervisor) {
    super(info);
    // Receive actor messages
    receive(initializingReceive());

//    Supervisor.lifecycle(supervisor(), JLifecycleManager.Startup);
  }

  @Override
  public void process(Configurations.SetupConfig config) {

  }

  // This receive is used when executing a Home command
  PartialFunction<Object, BoxedUnit> initializingReceive() {
    return ReceiveBuilder
      .match(Running.class, e -> {
        // When Running is received, transition to running Receive
        context().become(runningReceive());
      })
      .matchAny(x -> log.warning("Unexpected message in TromboneHCD:initializingReceive: " + x))
      .build();
  }

//class TromboneHCD(override val info: HcdInfo, supervisor: ActorRef) extends Hcd with HcdController {
//
//  import SingleAxisSimulator._
//  import TromboneHCD._
//
//  // Receive actor methods
//  def receive = initializingReceive
//
//  // Initialize axis from ConfigService
//  val axisConfig = getAxisConfig
//
//  // Create an axis for simulating trombone motion
//  val tromboneAxis: ActorRef = setupAxis(axisConfig)
//
//  // Initialize values -- This causes an update to the listener
//  implicit val timeout = Timeout(2.seconds)
//  // The current axis position from the hardware axis, initialize to default value
//  var current = Await.result((tromboneAxis ? InitialState).mapTo[AxisUpdate], timeout.duration)
//  var stats = Await.result((tromboneAxis ? GetStatistics).mapTo[AxisStatistics], timeout.duration)
//
//  // Keep track of the last SetupConfig to be received from external
//  var lastReceivedSC = SetupConfig(TromboneHCD.trombonePrefix)
//
//  def initializingReceive: Receive = {
//    case Running =>
//      // When Running is received, transition to running Receive
//      context.become(runningReceive)
//    case x => log.error(s"Unexpected message in TromboneHCD:initializingReceive: $x")
//  }
//
//  // Required setup for Lifecycle in order to get messages
//  supervisor ! Initialized
//  supervisor ! Started
//
//  // Idea syntax checking makes orElse orElse a syntax error though it isn't, but this makes it go away
//  def runningReceive = controllerReceive orElse runningReceive1
//
//  def runningReceive1 = runningReceivePF orElse runningReceive2
//
//  def runningReceive2 = lifecycleReceivePF orElse unhandledPF
//
//  def runningReceivePF: Receive = {
//    case GetAxisStats =>
//      tromboneAxis ! GetStatistics
//
//    case AxisStarted =>
//    //println("Axis Started")
//
//    case GetAxisConfig =>
//      val axisConfigState = defaultConfigState.madd(
//        lowLimitKey -> axisConfig.lowLimit,
//        lowUserKey -> axisConfig.lowUser,
//        highUserKey -> axisConfig.highUser,
//        highLimitKey -> axisConfig.highLimit,
//        homeValueKey -> axisConfig.home,
//        startValueKey -> axisConfig.startPosition,
//        stepDelayMSKey -> axisConfig.stepDelayMS
//      )
//      notifySubscribers(axisConfigState)
//
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
//
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
//
//  /**
//    * @param sc the config received
//    */
//  protected def process(sc: SetupConfig): Unit = {
//    import TromboneHCD._
//
//    log.debug(s"Trombone process received sc: $sc")
//
//    // Store the last received for diags
//    lastReceivedSC = sc
//
//    sc.configKey match {
//      case `axisMoveCK` =>
//        tromboneAxis ! Move(sc(positionKey).head, diagFlag = true)
//      case `axisDatumCK` =>
//        log.info("Received Datum")
//        tromboneAxis ! Datum
//      case `axisHomeCK` =>
//        tromboneAxis ! Home
//      case `axisCancelCK` =>
//        tromboneAxis ! CancelMove
//    }
//  }
//
//  def setupAxis(ac: AxisConfig): ActorRef = context.actorOf(SingleAxisSimulator.props(ac, Some(self)), "Test1")
//
//  // Utility functions
//  def getAxisConfig: AxisConfig = {
//    // Will be obtained from the
//    val name = getConfigString("axis-config.axisName")
//    val lowLimit = getConfigInt("axis-config.lowLimit")
//    val lowUser = getConfigInt("axis-config.lowUser")
//    val highUser = getConfigInt("axis-config.highUser")
//    val highLimit = getConfigInt("axis-config.highLimit")
//    val home = getConfigInt("axis-config.home")
//    val startPosition = getConfigInt("axis-config.startPosition")
//    val stepDelayMS = getConfigInt("axis-config.stepDelayMS")
//    AxisConfig(name, lowLimit, lowUser, highUser, highLimit, home, startPosition, stepDelayMS)
//  }
//
//  def getConfigString(name: String): String = context.system.settings.config.getString(s"csw.examples.Trombone.hcd.$name")
//
//  def getConfigInt(name: String): Int = context.system.settings.config.getInt(s"csw.examples.Trombone.hcd.$name")
//
//}



  // --- Static defs ---

  /**
   * Used to create the TromboneHCD actor
   *
   * @param info the HCD's prefix, used in configurations
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
  String componentName = "lgsTromboneHCD";
  ComponentType componentType = JComponentType.HCD;
  String componentClassName = "csw.examples.vslice.hcd.TromboneHCD";
  String trombonePrefix = "nfiraos.ncc.tromboneHCD";

  String tromboneAxisName = "tromboneAxis";

  String axisStatePrefix = trombonePrefix + ".axis1State";
  Configurations.ConfigKey axisStateCK = Configurations.ConfigKey.stringToConfigKey(axisStatePrefix);
  StringKey axisNameKey = new StringKey("axisName");
  Choice AXIS_IDLE = new Choice(AxisState.AXIS_IDLE.toString());
  Choice AXIS_MOVING = new Choice(AxisState.AXIS_MOVING.toString());
  Choice AXIS_ERROR = new Choice(AxisState.AXIS_ERROR.toString());
  ChoiceKey stateKey = new ChoiceKey("axisState",
    Choices.from(AXIS_IDLE.toString(), AXIS_MOVING.toString(), AXIS_ERROR.toString()));
  IntKey positionKey = new IntKey("position");
  BooleanKey inLowLimitKey = new BooleanKey("lowLimit");
  BooleanKey inHighLimitKey = new BooleanKey("highLimit");
  BooleanKey inHomeKey = new BooleanKey("homed");

//  val defaultAxisState = CurrentState(axisStateCK).madd(
//    axisNameKey -> tromboneAxisName,
//    stateKey -> AXIS_IDLE,
//    positionKey -> 0 withUnits encoder,
//    inLowLimitKey -> false,
//    inHighLimitKey -> false,
//    inHomeKey -> false
//  )
//
//  val axisStatsPrefix = s"$trombonePrefix.axisStats"
//  val axisStatsCK: ConfigKey = axisStatsPrefix
//  val datumCountKey = IntKey("initCount")
//  val moveCountKey = IntKey("moveCount")
//  val homeCountKey = IntKey("homeCount")
//  val limitCountKey = IntKey("limitCount")
//  val successCountKey = IntKey("successCount")
//  val failureCountKey = IntKey("failureCount")
//  val cancelCountKey = IntKey("cancelCount")
//  val defaultStatsState = CurrentState(axisStatsCK).madd(
//    axisNameKey -> tromboneAxisName,
//    datumCountKey -> 0,
//    moveCountKey -> 0,
//    homeCountKey -> 0,
//    limitCountKey -> 0,
//    successCountKey -> 0,
//    failureCountKey -> 0,
//    cancelCountKey -> 0
//  )
//
//  val axisConfigPrefix = s"$trombonePrefix.axisConfig"
//  val axisConfigCK: ConfigKey = axisConfigPrefix
//  // axisNameKey
//  val lowLimitKey = IntKey("lowLimit")
//  val lowUserKey = IntKey("lowUser")
//  val highUserKey = IntKey("highUser")
//  val highLimitKey = IntKey("highLimit")
//  val homeValueKey = IntKey("homeValue")
//  val startValueKey = IntKey("startValue")
//  val stepDelayMSKey = IntKey("stepDelayMS")
//  // No full default current state because it is determined at runtime
//  val defaultConfigState = CurrentState(axisConfigCK).madd(
//    axisNameKey -> tromboneAxisName
//  )
//
//  val axisMovePrefix = s"$trombonePrefix.move"
//  val axisMoveCK: ConfigKey = axisMovePrefix
//  def positionSC(value: Int):SetupConfig = SetupConfig(axisMoveCK).add(positionKey -> value withUnits encoder)
//
//  val axisDatumPrefix = s"$trombonePrefix.datum"
//  val axisDatumCK: ConfigKey = axisDatumPrefix
//  val datumSC = SetupConfig(axisDatumCK)
//
//  val axisHomePrefix = s"$trombonePrefix.home"
//  val axisHomeCK: ConfigKey = axisHomePrefix
//  val homeSC = SetupConfig(axisHomeCK)
//
//  val axisCancelPrefix = s"$trombonePrefix.cancel"
//  val axisCancelCK: ConfigKey = axisCancelPrefix
//  val cancelSC = SetupConfig(axisCancelCK)
//
//  // Testing messages for TromboneHCD
//  trait TromboneEngineering
//
//  case object GetAxisStats extends TromboneEngineering
//
//  case object GetAxisConfig extends TromboneEngineering
//}
//
///**
//  * Starts Assembly as a standalone application.
//  */
//object TromboneHCDApp extends App {
//
//  import TromboneHCD._
//  import csw.examples.vslice.shared.TromboneData._
//
//  private def setup: Config = ContainerComponent.parseStringConfig(testConf)
//
//  val componentConf = setup.getConfig(s"container.components.$componentName")
//  val testInfo = HcdInfo(componentName, trombonePrefix, componentClassName, DoNotRegister, Set(AkkaType), 1.second)
//  val hcdInfo = parseHcd(s"$componentName", componentConf).getOrElse(testInfo)
//
//  LocationService.initInterface()
//
//  println("Starting TromboneHCD: " + hcdInfo)
//
//  val supervisor = Supervisor(hcdInfo)
//}

}



