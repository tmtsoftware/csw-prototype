package csw.examples.vslice.hcd

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.hcd.SingleAxisSimulator.AxisConfig
import csw.services.ts.TimeService
import csw.services.ts.TimeService._

/**
  * This class provides a simulator of a single axis device for the purpose of testing TMT HCDs and Assemblies.
  *
  * @param axisConfig an AxisConfig object that contains a description of the axis
  * @param replyTo    an actor that will be updated with information while the axis executes
  */
class SingleAxisSimulator(val axisConfig: AxisConfig, replyTo: Option[ActorRef]) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

  import SingleAxisSimulator._
  import MotionWorker._

  // Check that the home position is not in a limit area - with this check it is not neceesary to check for limits after homing
  assert(axisConfig.home > axisConfig.lowUser, s"home position must be greater than lowUser value: ${axisConfig.lowUser}")
  assert(axisConfig.home < axisConfig.highUser, s"home position must be less than highUser value: ${axisConfig.highUser}")

  // The following are state information for the axis. These values are updated while the axis runs
  // This is safe because there is no way to change the variables other than within this actor
  // When created, the current is set to the start current
  var current = axisConfig.startPosition
  var inLowLimit = false
  var inHighLimit = false
  var inHome = false
  var axisState: AxisState = AXIS_IDLE

  // Statistics for status
  var initCount = 0
  // Number of init requests
  var moveCount = 0
  // Number of move requests
  var homeCount = 0
  // Number of home requests
  var limitCount = 0
  // Number of times in a limit
  var successCount = 0
  // Number of successful requests
  var failureCount = 0
  // Number of failed requests
  var cancelCount = 0 // Number of times a move has been cancelled

  // Set initially to the idle receive
  def receive = idleReceive

  // Short-cut to forward a messaage to the optional replyTo actor
  def update(replyTo: Option[ActorRef], msg: AnyRef) = replyTo.foreach(_ ! msg)

  def idleReceive: Receive = {
    case InitialState =>
      // Send the inital position for its state to the caller directly
      sender() ! getState

    case Init =>
      axisState = AXIS_MOVING
      update(replyTo, AxisStarted)
      // Takes some time and increments the current
      scheduleOnce(localTimeNow.plusSeconds(1), context.self, InitComplete)
      // Stats
      initCount += 1
      moveCount += 1

    case InitComplete =>
      // Set limits
      axisState = AXIS_IDLE
      // Power on causes motion of one unit!
      current += 1
      calcLimitsAndStats()
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case GetStatistics =>
      sender() ! AxisStatistics(axisConfig.axisName, initCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case Home =>
      axisState = AXIS_MOVING
      log.debug(s"AxisHome: $axisState")
      update(replyTo, AxisStarted)
      val props = MotionWorker.props(current, axisConfig.home, numSteps = 10, delayInMS = 100, self, diagFlag = false)
      val mw = context.actorOf(props, "homeWorker")
      context.become(homeReceive(mw))
      mw ! Start
      // Stats
      moveCount += 1

    case HomeComplete(finalPosition) =>
      //println("Home complete")
      axisState = AXIS_IDLE
      current = finalPosition
      // Set limits
      calcLimitsAndStats()
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case Move(targetPosition, diagFlag) =>
      log.debug(s"Move: $targetPosition")
      axisState = AXIS_MOVING
      update(replyTo, AxisStarted)
      val clampedTargetPosition = SingleAxisSimulator.limitMove(axisConfig, targetPosition)
      // The 200 ms here is the time for one step, so a 10 step move takes 2 seconds
      val props = MotionWorker.props(current, clampedTargetPosition, calcNumSteps(current, clampedTargetPosition), delayInMS = 200, self, diagFlag)
      val mw = context.actorOf(props, "moveWorker")
      context.become(moveReceive(mw))
      mw ! Start
      // Stats
      moveCount += 1

    case MoveComplete(finalPosition) =>
      log.debug("Move Complete")
      axisState = AXIS_IDLE
      current = finalPosition
      // Set limits
      calcLimitsAndStats()
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case x => log.error(s"Unexpected message in idleReceive: $x")
  }

  // This receive is used when executing a Home command
  def homeReceive(worker: ActorRef): Receive = {
    case Start =>
      log.debug("Home Start")
    case Tick(currentIn, stepsIn) =>
      current = currentIn
      // Send Update
      update(replyTo, getState)
    case End(finalpos) =>
      context.become(idleReceive)
      self ! HomeComplete(finalpos)
    case x => log.error(s"Unexpected message in homeReceive: $x")
  }

  def moveReceive(worker: ActorRef): Receive = {
    case Start =>
      log.debug("Move Start")
    case CancelMove =>
      worker ! Cancel
      // Stats
      cancelCount += 1
    case Tick(currentIn, stepsIn) =>
      current = currentIn
      log.debug("Move Update")
      // Send Update to caller
      update(replyTo, getState)
    case End(finalpos) =>
      log.debug("Move End")
      context.become(idleReceive)
      self ! MoveComplete(finalpos)
    case x => log.error(s"Unexpected message in moveReceive: $x")
  }

  def calcLimitsAndStats(): Unit = {
    inHighLimit = isHighLimit(axisConfig, current)
    inLowLimit = isLowLimit(axisConfig, current)
    if (inHighLimit || inLowLimit) limitCount += 1
    inHome = isHomed(axisConfig, current)
    if (inHome) homeCount += 1
  }

  def getState = AxisUpdate(axisConfig.axisName, axisState, current, inLowLimit, inHighLimit, inHome)
}

object SingleAxisSimulator {
  def props(axisConfig: AxisConfig, replyTo: Option[ActorRef] = None) = Props(classOf[SingleAxisSimulator], axisConfig, replyTo)

  case class AxisConfig(axisName: String, lowLimit: Int, lowUser: Int, highUser: Int, highLimit: Int, home: Int, startPosition: Int)

  trait AxisState

  case object AXIS_IDLE extends AxisState

  case object AXIS_MOVING extends AxisState

  case object AXIS_ERROR extends AxisState

  trait AxisRequest

  case object Home extends AxisRequest

  case object Init extends AxisRequest

  case class Move(position: Int, diagFlag: Boolean = false) extends AxisRequest

  case object CancelMove extends AxisRequest

  case object GetStatistics extends AxisRequest

  trait AxisResponse

  case object AxisStarted extends AxisResponse

  case object AxisFinished extends AxisResponse

  case class AxisUpdate(axisName: String, state: AxisState, current: Int, inLowLimit: Boolean, inHighLimit: Boolean, inHomed: Boolean) extends AxisResponse

  case class AxisFailure(reason: String) extends AxisResponse

  case class AxisStatistics(axisName: String, initCount: Int, moveCount: Int, homeCount: Int, limitCount: Int, successCount: Int, failureCount: Int, cancelCount: Int) extends AxisResponse {
    override def toString = s"name: $axisName, inits: $initCount, moves: $moveCount, homes: $homeCount, limits: $limitCount, success: $successCount, fails: $failureCount, cancels: $cancelCount"
  }

  // Internal
  trait InternalMessages

  case object InitComplete extends InternalMessages

  case class HomeComplete(position: Int) extends InternalMessages

  case class MoveComplete(position: Int) extends InternalMessages

  case object InitialState extends InternalMessages

  case object InitialStatistics extends InternalMessages

  // Helper functions in object for testing
  // limitMove clamps the request value to the hard limits
  def limitMove(ac: AxisConfig, request: Int) = Math.max(Math.min(request, ac.highLimit), ac.lowLimit)

  // Check to see if position is in the "limit" zones
  def isHighLimit(ac: AxisConfig, current: Int): Boolean = current >= ac.highUser

  def isLowLimit(ac: AxisConfig, current: Int): Boolean = current <= ac.lowUser

  def isHomed(ac: AxisConfig, current: Int): Boolean = current == ac.home

  // Determines the number of step updates between two positions. For long moves, there are 10, small moves 5
  def calcNumSteps(start: Int, end: Int): Int = {
    val diff = Math.abs(start - end)
    if (diff < 20) 2
    else if (diff > 500) 10
    else 5
  }

}

class MotionWorker(val start: Int, val destination: Int, val numSteps: Int, val delayInMS: Int, replyTo: ActorRef, diagFlag: Boolean) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

  import MotionWorker._
  import TimeService._

  val stepSize = calcStepSize(start, destination, numSteps)
  // Can be + or -
  var cancelFlag = false
  val delayInNanoSeconds: Long = delayInMS * 1000000
  var current = start

  def receive: Receive = {
    case Start =>
      if (diagFlag) diag("Starting", start, numSteps)
      replyTo ! Start
      scheduleOnce(localTimeNow.plusNanos(delayInNanoSeconds), context.self, Tick(start, numSteps))
    case Tick(currentIn, stepsIn) =>
      replyTo ! Tick(currentIn, stepsIn)
      val stepCount = stepsIn - 1
      // To fix rounding errors, if last tep set current to destination
      current = if (stepCount == 0) destination else currentIn + stepSize
      if (diagFlag) diag("Step", current, stepNumber(stepCount, numSteps))
      if (stepCount > 0 && !cancelFlag) scheduleOnce(localTimeNow.plusNanos(delayInNanoSeconds), context.self, Tick(current, stepCount))
      else self ! End(current)
    case Cancel =>
      if (diagFlag) log.debug("Worker received cancel")
      cancelFlag = true // Will cause to leave on next Tick
    case end@End(finalpos) =>
      replyTo ! end
      if (diagFlag) diag("End", finalpos, numSteps)
      // When the actor has nothing else to do, it should stop
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message in MotionWorker: $x")
  }

  def stepNumber(stepCount: Int, numSteps: Int) = numSteps - stepCount

  def calcStepSize(current: Int, destination: Int, steps: Int): Int = (destination - current) / steps

  def diag(hint: String, current: Int, stepValue: Int) = log.debug(s"$hint: start=$start, dest=$destination, step/totalSteps: $stepValue/$numSteps, current=$current")
}

object MotionWorker {
  def props(start: Int, destination: Int, numSteps: Int, delayInMS: Int, replyTo: ActorRef, diagFlag: Boolean) = Props(classOf[MotionWorker], start, destination, numSteps, delayInMS, replyTo, diagFlag)

  case object Start

  case class End(finalpos: Int)

  case class Tick(current: Int, stepCount: Int)

  case object Cancel

}
