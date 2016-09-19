package csw.examples.vslice.hcd

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.HcdController
import csw.services.loc.ComponentType
import csw.services.pkg.Component.HcdInfo
import csw.services.pkg.Supervisor3.{apply => _, _}
import csw.services.pkg.Hcd
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure.encoder
import csw.util.config._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions

/**
 * XXX TODO FIXME: supervisor arg overrides the supervisor inherited from Component
 */
class TromboneHCD(override val info: HcdInfo, supervisor: ActorRef) extends Hcd with HcdController {

  import SingleAxisSimulator._
  import TromboneHCD._

  // Receive actor methods
  def receive: Receive = initializingReceive

  // Initialize axis from ConfigService
  private[hcd] val axisConfig = getAxisConfig

  // Create an axis for simulating trombone motion
  val tromboneAxis: ActorRef = setupAxis(axisConfig)

  // Initialize values -- This causes an update to the listener
  implicit val timeout = Timeout(2.seconds)
  // The current axis position from the hardware axis, initialize to default value
  // (XXX TODO FIXME: Do we need to block here?)
  private[hcd] var current = Await.result((tromboneAxis ? InitialState).mapTo[AxisUpdate], timeout.duration)
  private[hcd] var stats = Await.result((tromboneAxis ? GetStatistics).mapTo[AxisStatistics], timeout.duration)

  // Keep track of the last SetupConfig to be received from external
  private var lastReceivedSC = SetupConfig(TromboneHCD.trombonePrefix)

  private def initializingReceive: Receive = {
    case Running =>
      // When Running is received, transition to running Receive
      context.become(runningReceive)
    case x => log.error(s"Unexpected message in TromboneHCD:initializingReceive: $x")
  }

  // Required setup for Lifecycle in order to get messages
  supervisor ! Initialized
  supervisor ! Started

  // Idea syntax checking makes orElse orElse a syntax error though it isn't, but this makes it go away
  private def runningReceive = controllerReceive orElse runningReceive1

  private def runningReceive1 = runningReceivePF orElse runningReceive2

  private def runningReceive2 = lifecycleReceivePF orElse unhandledPF

  private def runningReceivePF: Receive = {
    case GetAxisStats =>
      tromboneAxis ! GetStatistics

    case AxisStarted =>
    //println("Axis Started")

    case GetAxisConfig =>
      val axisConfigState = defaultConfigState.madd(
        lowLimitKey -> axisConfig.lowLimit,
        lowUserKey -> axisConfig.lowUser,
        highUserKey -> axisConfig.highUser,
        highLimitKey -> axisConfig.highLimit,
        homeValueKey -> axisConfig.home,
        startValueKey -> axisConfig.startPosition,
        stepDelayMSKey -> axisConfig.stepDelayMS
      )
      notifySubscribers(axisConfigState)

    case au @ AxisUpdate(_, axisState, currentPosition, inLowLimit, inHighLimit, inHomed) =>
      //log.info(s"Axis Update: $au")
      // Update actor state
      current = au
      val tromboneAxisState = defaultAxisState.madd(
        positionKey -> currentPosition withUnits encoder,
        stateKey -> axisState.toString,
        inLowLimitKey -> inLowLimit,
        inHighLimitKey -> inHighLimit,
        inHomeKey -> inHomed
      )
      notifySubscribers(tromboneAxisState)

    case as: AxisStatistics =>
      log.debug(s"AxisStatus: $as")
      // Update actor statistics
      stats = as
      val tromboneStats = defaultStatsState.madd(
        datumCountKey -> as.initCount,
        moveCountKey -> as.moveCount,
        limitCountKey -> as.limitCount,
        homeCountKey -> as.homeCount,
        successCountKey -> as.successCount,
        failureCountKey -> as.failureCount,
        cancelCountKey -> as.cancelCount
      )
      notifySubscribers(tromboneStats)
  }

  def lifecycleReceivePF: Receive = {
    case Running =>
      log.info("Received running")
      context.become(runningReceive)
    case RunningOffline =>
      log.info("Received running offline")
    case DoRestart =>
      log.info("Received dorestart")
    case DoShutdown =>
      log.info("Received doshutdown")
      // Just say complete for now
      supervisor ! ShutdownComplete
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      log.info(s"Received failed state: $state for reason: $reason")
  }

  private def unhandledPF: Receive = {
    case x => log.error(s"Unexpected message in TromboneHCD:unhandledPF: $x")
  }

  /**
   * @param sc the config received
   */
  protected def process(sc: SetupConfig): Unit = {
    import TromboneHCD._

    log.debug(s"Trombone process received sc: $sc")

    // Store the last received for diags
    lastReceivedSC = sc

    sc.configKey match {
      case `axisMoveCK` =>
        tromboneAxis ! Move(sc(positionKey).head, diagFlag = true)
      case `axisDatumCK` =>
        log.info("Received Datum")
        tromboneAxis ! Datum
      case `axisHomeCK` =>
        tromboneAxis ! Home
      case `axisCancelCK` =>
        tromboneAxis ! CancelMove
    }
  }

  private def setupAxis(ac: AxisConfig): ActorRef = context.actorOf(SingleAxisSimulator.props(ac, Some(self)), "Test1")

  // Utility functions
  private def getAxisConfig: AxisConfig = {
    // Will be obtained from the
    val name = getConfigString("axis-config.axisName")
    val lowLimit = getConfigInt("axis-config.lowLimit")
    val lowUser = getConfigInt("axis-config.lowUser")
    val highUser = getConfigInt("axis-config.highUser")
    val highLimit = getConfigInt("axis-config.highLimit")
    val home = getConfigInt("axis-config.home")
    val startPosition = getConfigInt("axis-config.startPosition")
    val stepDelayMS = getConfigInt("axis-config.stepDelayMS")
    AxisConfig(name, lowLimit, lowUser, highUser, highLimit, home, startPosition, stepDelayMS)
  }

  private def getConfigString(name: String): String = context.system.settings.config.getString(s"csw.examples.Trombone.hcd.$name")

  private def getConfigInt(name: String): Int = context.system.settings.config.getInt(s"csw.examples.Trombone.hcd.$name")

}

object TromboneHCD {
  def props(hcdInfo: HcdInfo, supervisor: ActorRef) = Props(classOf[TromboneHCD], hcdInfo, supervisor)

  // HCD Info
  val componentName = "lgsTromboneHCD"
  val componentType = ComponentType.HCD
  val componentClassName = "csw.examples.vslice.hcd.TromboneHCD"
  val trombonePrefix = "nfiraos.ncc.tromboneHCD"

  val tromboneAxisName = "tromboneAxis"

  val axisStatePrefix = s"$trombonePrefix.axis1State"
  val axisStateCK: ConfigKey = axisStatePrefix
  val axisNameKey = StringKey("axisName")
  //val stateKey = StringKey("axisState")
  //val IDLE = SingleAxisSimulator.AXIS_IDLE.toString
  //val MOVING = SingleAxisSimulator.AXIS_MOVING.toString
  //val ERROR = SingleAxisSimulator.AXIS_ERROR.toString
  val AXIS_IDLE = Choice(SingleAxisSimulator.AXIS_IDLE.toString)
  val AXIS_MOVING = Choice(SingleAxisSimulator.AXIS_MOVING.toString)
  val AXIS_ERROR = Choice(SingleAxisSimulator.AXIS_ERROR.toString)
  val stateKey = ChoiceKey("axisState", AXIS_IDLE, AXIS_MOVING, AXIS_ERROR)
  val positionKey = IntKey("position")
  val inLowLimitKey = BooleanKey("lowLimit")
  val inHighLimitKey = BooleanKey("highLimit")
  val inHomeKey = BooleanKey("homed")

  val defaultAxisState = CurrentState(axisStateCK).madd(
    axisNameKey -> tromboneAxisName,
    stateKey -> AXIS_IDLE,
    positionKey -> 0 withUnits encoder,
    inLowLimitKey -> false,
    inHighLimitKey -> false,
    inHomeKey -> false
  )

  val axisStatsPrefix = s"$trombonePrefix.axisStats"
  val axisStatsCK: ConfigKey = axisStatsPrefix
  val datumCountKey = IntKey("initCount")
  val moveCountKey = IntKey("moveCount")
  val homeCountKey = IntKey("homeCount")
  val limitCountKey = IntKey("limitCount")
  val successCountKey = IntKey("successCount")
  val failureCountKey = IntKey("failureCount")
  val cancelCountKey = IntKey("cancelCount")
  val defaultStatsState = CurrentState(axisStatsCK).madd(
    axisNameKey -> tromboneAxisName,
    datumCountKey -> 0,
    moveCountKey -> 0,
    homeCountKey -> 0,
    limitCountKey -> 0,
    successCountKey -> 0,
    failureCountKey -> 0,
    cancelCountKey -> 0
  )

  val axisConfigPrefix = s"$trombonePrefix.axisConfig"
  val axisConfigCK: ConfigKey = axisConfigPrefix
  // axisNameKey
  val lowLimitKey = IntKey("lowLimit")
  val lowUserKey = IntKey("lowUser")
  val highUserKey = IntKey("highUser")
  val highLimitKey = IntKey("highLimit")
  val homeValueKey = IntKey("homeValue")
  val startValueKey = IntKey("startValue")
  val stepDelayMSKey = IntKey("stepDelayMS")
  // No full default current state because it is determined at runtime
  val defaultConfigState = CurrentState(axisConfigCK).madd(
    axisNameKey -> tromboneAxisName
  )

  val axisMovePrefix = s"$trombonePrefix.move"
  val axisMoveCK: ConfigKey = axisMovePrefix
  def positionSC(value: Int): SetupConfig = SetupConfig(axisMoveCK).add(positionKey -> value withUnits encoder)

  val axisDatumPrefix = s"$trombonePrefix.datum"
  val axisDatumCK: ConfigKey = axisDatumPrefix
  val datumSC = SetupConfig(axisDatumCK)

  val axisHomePrefix = s"$trombonePrefix.home"
  val axisHomeCK: ConfigKey = axisHomePrefix
  val homeSC = SetupConfig(axisHomeCK)

  val axisCancelPrefix = s"$trombonePrefix.cancel"
  val axisCancelCK: ConfigKey = axisCancelPrefix
  val cancelSC = SetupConfig(axisCancelCK)

  // Testing messages for TromboneHCD
  trait TromboneEngineering

  case object GetAxisStats extends TromboneEngineering

  case object GetAxisConfig extends TromboneEngineering
}
