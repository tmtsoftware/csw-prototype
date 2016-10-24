package csw.examples.vslice.hcd

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ts.TimeService
import csw.services.ts.TimeService._

/**
 * This class provides a simulator of a single axis device for the purpose of testing TMT HCDs and Assemblies.
 *
 * @param axisConfig an AxisConfig object that contains a description of the axis
 * @param replyTo    an actor that will be updated with information while the axis executes
 */
class SingleAxisSimulator(val axisConfig: AxisConfig, replyTo: Option[ActorRef]) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

  import MotionWorker._
  import SingleAxisSimulator._

  // Check that the home position is not in a limit area - with this check it is not neceesary to check for limits after homing
  assert(axisConfig.home > axisConfig.lowUser, s"home position must be greater than lowUser value: ${axisConfig.lowUser}")
  assert(axisConfig.home < axisConfig.highUser, s"home position must be less than highUser value: ${axisConfig.highUser}")

  // The following are state information for the axis. These values are updated while the axis runs
  // This is safe because there is no way to change the variables other than within this actor
  // When created, the current is set to the start current
  private[hcd] var current = axisConfig.startPosition
  private[hcd] var inLowLimit = false
  private[hcd] var inHighLimit = false
  private[hcd] var inHome = false
  private[hcd] var axisState: AxisState = AXIS_IDLE

  // Statistics for status
  private[hcd] var initCount = 0
  // Number of init requests
  private[hcd] var moveCount = 0
  // Number of move requests
  private[hcd] var homeCount = 0
  // Number of home requests
  private[hcd] var limitCount = 0
  // Number of times in a limit
  private[hcd] var successCount = 0
  // Number of successful requests
  private[hcd] var failureCount = 0
  // Number of failed requests
  private[hcd] var cancelCount = 0 // Number of times a move has been cancelled

  // Set initially to the idle receive
  def receive: Receive = idleReceive

  // Short-cut to forward a messaage to the optional replyTo actor
  private def update(replyTo: Option[ActorRef], msg: AnyRef) = replyTo.foreach(_ ! msg)

  private def idleReceive: Receive = {
    case InitialState =>
      // Send the inital position for its state to the caller directly
      sender() ! getState

    case Datum =>
      axisState = AXIS_MOVING
      update(replyTo, AxisStarted)
      // Takes some time and increments the current
      scheduleOnce(localTimeNow.plusSeconds(1), context.self, DatumComplete)
      // Stats
      initCount += 1
      moveCount += 1

    case DatumComplete =>
      // Set limits
      axisState = AXIS_IDLE
      // Power on causes motion of one unit!
      current += 1
      checkLimits()
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case GetStatistics =>
      sender() ! AxisStatistics(axisConfig.axisName, initCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case PublishAxisUpdate =>
      update(replyTo, getState)

    case Home =>
      axisState = AXIS_MOVING
      log.debug(s"AxisHome: $axisState")
      update(replyTo, AxisStarted)
      val props = MotionWorker.props(current, axisConfig.home, delayInMS = 100, self, diagFlag = false)
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
      checkLimits()
      if (inHome) homeCount += 1
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
      val props = MotionWorker.props(current, clampedTargetPosition, delayInMS = axisConfig.stepDelayMS, self, diagFlag)
      val mw = context.actorOf(props, s"moveWorker-${System.currentTimeMillis}")
      context.become(moveReceive(mw))
      mw ! Start
      // Stats
      moveCount += 1

    case MoveComplete(finalPosition) =>
      log.debug("Move Complete")
      axisState = AXIS_IDLE
      current = finalPosition
      // Set limits
      checkLimits()
      // Do the count of limits
      if (inHighLimit || inLowLimit) limitCount += 1
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case CancelMove =>
      log.debug("Received Cancel Move while idle :-(")
      // Stats
      cancelCount += 1

    case x => log.error(s"Unexpected message in idleReceive: $x")
  }

  // This receive is used when executing a Home command
  private def homeReceive(worker: ActorRef): Receive = {
    case Start =>
      log.debug("Home Start")
    case Tick(currentIn) =>
      current = currentIn
      // Set limits - this was a bug - need to do this after every step
      checkLimits()
      // Send Update
      update(replyTo, getState)
    case End(finalpos) =>
      context.become(idleReceive)
      self ! HomeComplete(finalpos)
    case x => log.error(s"Unexpected message in homeReceive: $x")
  }

  private def moveReceive(worker: ActorRef): Receive = {
    case Start =>
      log.debug("Move Start")
    case CancelMove =>
      log.debug("Cancel MOVE")
      worker ! Cancel
      // Stats
      cancelCount += 1
    case Move(targetPosition, _) =>
      // When this is received, we update the final position while a motion is happening
      worker ! MoveUpdate(targetPosition)
    case Tick(currentIn) =>
      current = currentIn
      log.debug("Move Update")
      // Set limits - this was a bug - need to do this after every step
      checkLimits()
      // Send Update to caller
      update(replyTo, getState)
    case End(finalpos) =>
      log.debug("Move End")
      context.become(idleReceive)
      self ! MoveComplete(finalpos)
    case x => log.error(s"Unexpected message in moveReceive: $x")
  }

  private def checkLimits(): Unit = {
    inHighLimit = isHighLimit(axisConfig, current)
    inLowLimit = isLowLimit(axisConfig, current)
    inHome = isHomed(axisConfig, current)
  }

  private def getState = AxisUpdate(axisConfig.axisName, axisState, current, inLowLimit, inHighLimit, inHome)
}

object SingleAxisSimulator {
  def props(axisConfig: AxisConfig, replyTo: Option[ActorRef] = None) = Props(classOf[SingleAxisSimulator], axisConfig, replyTo)

  //  case class AxisConfig(axisName: String, lowLimit: Int, lowUser: Int, highUser: Int, highLimit: Int, home: Int, startPosition: Int, stepDelayMS: Int)

  trait AxisState
  case object AXIS_IDLE extends AxisState
  case object AXIS_MOVING extends AxisState
  case object AXIS_ERROR extends AxisState

  trait AxisRequest
  case object Home extends AxisRequest
  case object Datum extends AxisRequest
  case class Move(position: Int, diagFlag: Boolean = false) extends AxisRequest
  case object CancelMove extends AxisRequest
  case object GetStatistics extends AxisRequest
  case object PublishAxisUpdate extends AxisRequest

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
  case object DatumComplete extends InternalMessages
  case class HomeComplete(position: Int) extends InternalMessages
  case class MoveComplete(position: Int) extends InternalMessages
  case object InitialState extends InternalMessages
  case object InitialStatistics extends InternalMessages

  // Helper functions in object for testing
  // limitMove clamps the request value to the hard limits
  def limitMove(ac: AxisConfig, request: Int): Int = Math.max(Math.min(request, ac.highLimit), ac.lowLimit)

  // Check to see if position is in the "limit" zones
  def isHighLimit(ac: AxisConfig, current: Int): Boolean = current >= ac.highUser

  def isLowLimit(ac: AxisConfig, current: Int): Boolean = current <= ac.lowUser

  def isHomed(ac: AxisConfig, current: Int): Boolean = current == ac.home

}

class MotionWorker(val start: Int, val destinationIn: Int, val delayInMS: Int, replyTo: ActorRef, diagFlag: Boolean) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

  import MotionWorker._
  import TimeService._

  private[hcd] var destination = destinationIn

  private var numSteps = calcNumSteps(start, destination)
  private var stepSize = calcStepSize(start, destination, numSteps)
  private var stepCount = 0
  // Can be + or -
  private var cancelFlag = false
  private[hcd] val delayInNanoSeconds: Long = delayInMS * 1000000
  private var current = start

  override def receive: Receive = {
    case Start =>
      if (diagFlag) diag("Starting", start, numSteps)
      replyTo ! Start
      scheduleOnce(localTimeNow.plusNanos(delayInNanoSeconds), context.self, Tick(start + stepSize))
    case Tick(currentIn) =>
      replyTo ! Tick(currentIn)

      current = currentIn
      // Keep a count of steps in this MotionWorker instance
      stepCount += 1

      // If we are on the last step of a move, then distance equals 0
      val distance = calcDistance(current, destination)
      val done = distance == 0
      // To fix rounding errors, if last step set current to destination
      val last = lastStep(current, destination, stepSize)
      val nextPos = if (last) destination else currentIn + stepSize
      if (diagFlag) log.info(s"currentIn: $currentIn, distance: $distance, stepSize: $stepSize, done: $done, nextPos: $nextPos")

      if (!done && !cancelFlag) scheduleOnce(localTimeNow.plusNanos(delayInNanoSeconds), context.self, Tick(nextPos))
      else self ! End(current)
    case MoveUpdate(destinationUpdate) =>
      destination = destinationUpdate
      numSteps = calcNumSteps(current, destination)
      stepSize = calcStepSize(current, destination, numSteps)
      log.info(s"NEW dest: $destination, numSteps: $numSteps, stepSize: $stepSize")
    case Cancel =>
      if (diagFlag) log.debug("Worker received cancel")
      cancelFlag = true // Will cause to leave on next Tick
    case end @ End(finalpos) =>
      replyTo ! end
      if (diagFlag) diag("End", finalpos, numSteps)
      // When the actor has nothing else to do, it should stop
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message in MotionWorker: $x")
  }

  def diag(hint: String, current: Int, stepValue: Int): Unit = log.info(s"$hint: start=$start, dest=$destination, totalSteps: $stepValue, current=$current")
}

object MotionWorker {
  def props(start: Int, destination: Int, delayInMS: Int, replyTo: ActorRef, diagFlag: Boolean) = Props(classOf[MotionWorker], start, destination, delayInMS, replyTo, diagFlag)

  trait MotionWorkerMsgs
  case object Start extends MotionWorkerMsgs
  case class End(finalpos: Int) extends MotionWorkerMsgs
  case class Tick(current: Int) extends MotionWorkerMsgs
  case class MoveUpdate(destination: Int) extends MotionWorkerMsgs
  case object Cancel extends MotionWorkerMsgs

  def calcNumSteps(start: Int, end: Int): Int = {
    val diff = Math.abs(start - end)
    if (diff <= 5) 1
    else if (diff <= 20) 2
    else if (diff <= 500) 5
    else 10
  }

  def calcStepSize(current: Int, destination: Int, steps: Int): Int = (destination - current) / steps

  //def stepNumber(stepCount: Int, numSteps: Int) = numSteps - stepCount

  def calcDistance(current: Int, destination: Int): Int = Math.abs(current - destination)
  def lastStep(current: Int, destination: Int, stepSize: Int): Boolean = calcDistance(current, destination) <= Math.abs(stepSize)

}
