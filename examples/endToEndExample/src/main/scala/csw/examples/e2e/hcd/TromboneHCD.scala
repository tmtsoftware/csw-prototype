package csw.examples.e2e.hcd

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import csw.services.ccs.HcdController
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.{ComponentType, LocationService}
import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
import csw.services.pkg.ContainerComponent._
import csw.services.pkg.Supervisor.{apply => _}
import csw.services.pkg.{ContainerComponent, Hcd, Supervisor}
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure.encoder
import csw.util.config._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.implicitConversions

/**
  * TMT Source Code: 6/20/16.
  */
class TromboneHCD(info: HcdInfo) extends Hcd with HcdController with ActorLogging {

  import SingleAxisSimulator._
  import TromboneHCD._

  //import LifecycleHandler._

  //lifecycle(supervisor, Initialize)

  // Receive actor methods
  def receive = tromboneReceive //initializingReceive

  // Initialize axis from ConfigService
  val axisConfig = getAxisConfig

  // Create an axis for simulating trombone motion
  val tromboneAxis: ActorRef = setupAxis(axisConfig)

  // Initialize values -- This causes an update to the listener
  implicit val timeout = Timeout(2.seconds)
  // The current axis position from the hardware axis, initialize to default value
  var current = Await.result((tromboneAxis ? InitialState).mapTo[AxisUpdate], timeout.duration)
  var stats = Await.result((tromboneAxis ? GetStatistics).mapTo[AxisStatistics], timeout.duration)

  // Keep track of the last SetupConfig to be received from external
  var lastReceivedSC = SetupConfig(TromboneHCD.trombonePrefix)

  // send Initialized
  // send Started

  // When Running is received, transition to running Receive
  //context.become(tromboneReceive)

  def tromboneReceive = controllerReceive orElse runningReceive

  def runningReceive: Receive = {
    case AxisStats =>
      tromboneAxis ! GetStatistics

    case AxisStarted =>
    //println("Axis Started")

    case au@AxisUpdate(_, axisState, currentPosition, inLowLimit, inHighLimit, inHomed) =>
      //log.debug(s"Axis Update: $au")
      // Update actor state
      current = au
      val tromboneAxisState = defaultAxisState.madd(
        positionKey -> currentPosition withUnits encoder,
        stateKey -> axisState.toString,
        lowLimitKey -> inLowLimit,
        highLimitKey -> inHighLimit,
        homedKey -> inHomed
      )
      notifySubscribers(tromboneAxisState)

    case as: AxisStatistics =>
      log.debug(s"AxisStatus: $as")
      // Update actor statistics
      stats = as
      val tromboneStats = defaultStatsState.madd(
        initCountKey -> as.initCount,
        moveCountKey -> as.moveCount,
        limitCountKey -> as.limitCount,
        homeCountKey -> as.homeCount,
        successCountKey -> as.successCount,
        failureCountKey -> as.failureCount,
        cancelCountKey -> as.cancelCount
      )
      notifySubscribers(tromboneStats)

    case x => log.error(s"Unexpected message in TromboneHCD:runningReceive: $x")
  }

  /**
    * @param sc the config received
    */
  protected def process(sc: SetupConfig): Unit = {
    import TromboneHCD._

    log.info(s"Trombone process received sc: $sc")

    // Store the last received for diags
    lastReceivedSC = sc

    sc.configKey match {
      case `axisMoveCK` =>
        tromboneAxis ! Move(sc(positionKey).head, diagFlag = false)
      case `axisInitCK` =>
        tromboneAxis ! Init
      case `axisHomeCK` =>
        tromboneAxis ! Home
      case `axisCancelCK` =>
        tromboneAxis ! CancelMove
    }
  }

  def setupAxis(ac: AxisConfig): ActorRef = context.actorOf(SingleAxisSimulator.props(ac, Some(self)), "Test1")

  // Utility functions
  def getAxisConfig: AxisConfig = {
    // Will be obtained from the
    val name = getConfigString("axis-config.axisName")
    val lowLimit = getConfigInt("axis-config.lowLimit")
    val lowUser = getConfigInt("axis-config.lowUser")
    val highUser = getConfigInt("axis-config.highUser")
    val highLimit = getConfigInt("axis-config.highLimit")
    val home = getConfigInt("axis-config.home")
    val startPosition = getConfigInt("axis-config.startPosition")
    AxisConfig(name, lowLimit, lowUser, highUser, highLimit, home, startPosition)
  }

  def getConfigString(name: String): String = context.system.settings.config.getString(s"csw.examples.Trombone.$name")

  def getConfigInt(name: String): Int = context.system.settings.config.getInt(s"csw.examples.Trombone.$name")

}

object TromboneHCD {
  def props(hcdInfo: HcdInfo) = Props(classOf[TromboneHCD], hcdInfo)

  // HCD Info
  val componentName = "lgsTromboneHCD"
  val componentType = ComponentType.HCD
  val componentClassName = "csw.examples.e2e.hcd.TromboneHCD"
  val trombonePrefix = "nfiraos.ncc.tromboneHCD"

  val axisStatePrefix = s"$trombonePrefix.axis1State"
  val axisNameKey = StringKey("axisName")
  val stateKey = StringKey("defaultAxisState")
  val IDLE = SingleAxisSimulator.AXIS_IDLE.toString
  val MOVING = SingleAxisSimulator.AXIS_MOVING.toString
  val ERROR = SingleAxisSimulator.AXIS_ERROR.toString
  //val stateKey = ChoiceKey("defaultAxisState", Choices(IDLE, MOVING, ERROR))
  val positionKey = IntKey("position")
  val lowLimitKey = BooleanKey("lowLimit")
  val highLimitKey = BooleanKey("highLimit")
  val homedKey = BooleanKey("homed")

  val axisName = "tromboneAxis"
  val defaultAxisState = CurrentState(axisStatePrefix).madd(
    axisNameKey -> axisName,
    stateKey -> IDLE,
    positionKey -> 0 withUnits encoder,
    lowLimitKey -> false,
    highLimitKey -> false,
    homedKey -> false
  )

  val axisStatsPrefix = s"$trombonePrefix.axisStats"
  val axisStatsCK: ConfigKey = axisStatsPrefix
  val initCountKey = IntKey("initCount")
  val moveCountKey = IntKey("moveCount")
  val homeCountKey = IntKey("homeCount")
  val limitCountKey = IntKey("limitCount")
  val successCountKey = IntKey("successCount")
  val failureCountKey = IntKey("failureCount")
  val cancelCountKey = IntKey("cancelCount")

  val defaultStatsState = CurrentState(axisStatsCK).madd(
    axisNameKey -> axisName,
    initCountKey -> 0,
    moveCountKey -> 0,
    homeCountKey -> 0,
    limitCountKey -> 0,
    successCountKey -> 0,
    failureCountKey -> 0,
    cancelCountKey -> 0
  )

  val axisMovePrefix = s"$trombonePrefix.move"
  val axisMoveCK: ConfigKey = axisMovePrefix
  val positionSC = SetupConfig(axisMoveCK).add(positionKey -> 300 withUnits encoder)

  val axisInitPrefix = s"$trombonePrefix.init"
  val axisInitCK: ConfigKey = axisInitPrefix
  val initSC = SetupConfig(axisInitCK)

  val axisHomePrefix = s"$trombonePrefix.home"
  val axisHomeCK: ConfigKey = axisHomePrefix
  val homeSC = SetupConfig(axisHomeCK)

  val axisCancelPrefix = s"$trombonePrefix.cancel"
  val axisCancelCK: ConfigKey = axisCancelPrefix
  val cancelSC = SetupConfig(axisCancelCK)

  // Testing messages for TromboneHCD
  trait TromboneEngineering

  case object AxisStats extends TromboneEngineering

}

/**
  * Starts Assembly as a standalone application.
  */
object TromboneHCDApp extends App {

  import TromboneHCD._
  import csw.examples.e2e.shared.TromboneData._

  private def setup: Config = ContainerComponent.parseStringConfig(testConf)

  val componentConf = setup.getConfig(s"container.components.$componentName")
  val testInfo = HcdInfo(componentName, trombonePrefix, componentClassName, DoNotRegister, Set(AkkaType), 1.second)
  val hcdInfo = parseHcd(s"$componentName", componentConf).getOrElse(testInfo)

  LocationService.initInterface()

  println("Starting TromboneHCD: " + hcdInfo)

  val supervisor = Supervisor(hcdInfo)
}