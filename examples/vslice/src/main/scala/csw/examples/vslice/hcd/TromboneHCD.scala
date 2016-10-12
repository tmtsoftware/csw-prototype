package csw.examples.vslice.hcd

import java.io.File

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.HcdController
import csw.services.cs.akka.ConfigServiceClient
import csw.services.loc.ComponentType
import csw.services.pkg.Component.HcdInfo
import csw.services.pkg.Supervisor3._
import csw.services.pkg.Hcd
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure.encoder
import csw.util.config._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

/**
 * Example main class for the Trombone HCD
 */
class TromboneHCD(override val info: HcdInfo, supervisor: ActorRef) extends Hcd with HcdController {

  import context.dispatcher
  import SingleAxisSimulator._
  import TromboneHCD._

  implicit val timeout = Timeout(2.seconds)

  // Note: The following could be passed as parameters to context.become(), but are declared as
  // vars here so that they can be examined by test cases

  var axisConfig: AxisConfig = _

  // Create an axis for simulating trombone motion
  var tromboneAxis: ActorRef = _

  // Initialize values -- This causes an update to the listener
  // The current axis position from the hardware axis, initialize to default value
  var current: AxisUpdate = _
  var stats: AxisStatistics = _

  // Keep track of the last SetupConfig to be received from external
  private var lastReceivedSC = SetupConfig(TromboneHCD.trombonePrefix)

  // XXX TODO allan: change this to block for simplicity?  XXX

  // Get the axis config file from the config service, then use it to start the tromboneAxis actor
  // and get the current values. Once that is done, we can tell the supervisor actor that we are ready
  // and then wait for the Running message from the supervisor before going to the running state.
  for {
    // Initialize axis from ConfigService
    axisConfig <- getAxisConfig

    // Create an axis for simulating trombone motion
    tromboneAxis <- Future.successful(setupAxis(axisConfig))

    // Initialize values -- This causes an update to the listener
    // The current axis position from the hardware axis, initialize to default value
    current <- (tromboneAxis ? InitialState).mapTo[AxisUpdate]
    stats <- (tromboneAxis ? GetStatistics).mapTo[AxisStatistics]

  } {
    this.tromboneAxis = tromboneAxis
    this.axisConfig = axisConfig
    this.current = current
    this.stats = stats
    context.become(initializingReceive)

    // Required setup for Lifecycle in order to get messages
    supervisor ! Initialized
    supervisor ! Started
  }

  // Receive actor methods
  override def receive: Receive = publisherReceive orElse {
    case x => log.error(s"Unexpected message in TromboneHCD (Not initialized yet): $x")
  }

  // State where we are waiting for the Running message from the supervisor
  private def initializingReceive: Receive = publisherReceive orElse {
    case Running =>
      // When Running is received, transition to running Receive
      context.become(runningReceive)

    case x => log.error(s"Unexpected message in TromboneHCD (Not running yet): $x")
  }

  // Running state
  private def runningReceive: Receive = controllerReceive orElse {
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

    case GetAxisStats =>
      tromboneAxis ! GetStatistics

    case GetAxisUpdate =>
      tromboneAxis ! PublishAxisUpdate

    case GetAxisUpdateNow =>
      sender() ! current

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
      // Update actor state
      this.current = au
      context.become(runningReceive)
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
      this.stats = as
      context.become(runningReceive)
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

    case x => log.error(s"Unexpected message in TromboneHCD (running state): $x")
  }

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
  private def getAxisConfig: Future[AxisConfig] = {
    // This is required by the ConfigServiceClient
    implicit val system = context.system

    // Get the trombone config file from the config service, or use the given resource file if that doesn't work
    val tromboneConfigFile = new File("trombone/tromboneHCD.conf")
    val resource = new File("tromboneHCD.conf")
    val f = ConfigServiceClient.getConfigFromConfigService(tromboneConfigFile, resource = Some(resource))

    // Convert the future (optional) config to an AxisConfig
    f.map(configOpt => AxisConfig(configOpt.get))
  }
}

object TromboneHCD {
  def props(hcdInfo: HcdInfo, supervisor: ActorRef) = Props(classOf[TromboneHCD], hcdInfo, supervisor)

  // HCD Info
  val componentName = "tromboneHCD"
  val componentType = ComponentType.HCD
  val componentClassName = "csw.examples.vslice.hcd.TromboneHCD"
  val trombonePrefix = "nfiraos.ncc.tromboneHCD"

  val tromboneAxisName = "tromboneAxis"

  val axisStatePrefix = s"$trombonePrefix.axis1State"
  val axisStateCK: ConfigKey = axisStatePrefix
  val axisNameKey = StringKey("axisName")
  val AXIS_IDLE = Choice(SingleAxisSimulator.AXIS_IDLE.toString)
  val AXIS_MOVING = Choice(SingleAxisSimulator.AXIS_MOVING.toString)
  val AXIS_ERROR = Choice(SingleAxisSimulator.AXIS_ERROR.toString)
  val stateKey = ChoiceKey("axisState", AXIS_IDLE, AXIS_MOVING, AXIS_ERROR)
  val positionKey = IntKey("position")
  val positionUnits = encoder
  val inLowLimitKey = BooleanKey("lowLimit")
  val inHighLimitKey = BooleanKey("highLimit")
  val inHomeKey = BooleanKey("homed")

  private val defaultAxisState = CurrentState(axisStateCK).madd(
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
  private val defaultStatsState = CurrentState(axisStatsCK).madd(
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
  private val defaultConfigState = CurrentState(axisConfigCK).madd(
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

  /**
   * Returns an AxisUpdate through subscribers
   */
  case object GetAxisUpdate extends TromboneEngineering

  /**
   * Directly returns an AxisUpdate to sender
   */
  case object GetAxisUpdateNow extends TromboneEngineering

  case object GetAxisConfig extends TromboneEngineering

}
