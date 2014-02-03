package org.tmt.csw.cmd.akka

import akka.actor.{Cancellable, ActorLogging, Actor, Props}
import scala.concurrent.duration.FiniteDuration
import scala.Some

object CommandServiceStatusMonitor {

  // Object passed in timeout messages
  private case class Timeout(id: Int)

  // Used so we can cancel unused timers before they cause dead letter log messages
  private case class TimerInfo(timeout: Timeout, timer: Cancellable)

  /**
   * Props for creating this actor (see CommandServiceMonitor constructor)
   */
  def props(timeout: FiniteDuration, runId: RunId): Props =
    Props(classOf[CommandServiceStatusMonitor], timeout, runId)
}

/**
 * Implements long polling requests on the command service actor to get the status of a previously submitted command
 * (Original algorithm based on Spray example at http://hseeberger.github.io/blog/2013/07/22/gabbler-part3/).
 * This actor uses "become" to change state, depending on whether it is waiting for the command status message
 * or for a request for it (called a "completer" here). The "completer" object can be used to complete the
 * request for the command status, once it is known. If no status message is received within the given
 * timeDuration, the previous command status is returned (for example, Pending or Busy).
 *
 * @param timeoutDuration automatically timeout requests for command status after this amount of time
 * @param runId the runId for the command being monitored
 */
final class CommandServiceStatusMonitor(timeoutDuration: FiniteDuration, runId: RunId) extends Actor with ActorLogging {
  import CommandServiceClientHelper._
  import CommandServiceStatusMonitor._
  import context.dispatcher

  def receive: Receive =
    waiting(scheduleTimeout(Timeout(0)), CommandStatus.Pending(runId).asInstanceOf[CommandStatus])

  // Wait for the command status or for a request for it (completer).
  // Give up if nothing happens in the required time.
  private def waiting(timerInfo: TimerInfo, previouStatus: CommandStatus): Receive = {
    case completer: CommandStatusCompleter =>
      log.debug(s"Received completer (waiting)")
      context become waitingForStatus(completer, newTimeout(timerInfo), previouStatus)
    case status: CommandStatus =>
      log.debug(s"Received command status $status (waiting)")
      context become waitingForCompleter(status, timerInfo, status)
    case timerInfo.`timeout` =>
      log.debug(s"Received timeout while waiting: stopping")
      context.stop(self)
    case Timeout(t) => // ignore other timeouts
  }

  // We have a request for command status (completer), but no status value to return yet.
  // Wait for a command status message to arrive and if we timeout, return the previous status.
  // The requester should then try again later.
  private def waitingForStatus(completer: CommandStatusCompleter, timerInfo: TimerInfo, previousStatus: CommandStatus): Receive = {
    case completer: CommandStatusCompleter =>
      log.debug(s"Received completer (waiting for status)")
      context become waitingForStatus(completer, newTimeout(timerInfo), previousStatus)
    case status: CommandStatus =>
      log.debug(s"Received command status $status (waiting for status)")
      completeAndWait(completer, status, timerInfo)
    case timerInfo.`timeout` =>
      log.debug(s"Received timeout while waiting for command status: completing")
      completeAndWait(completer, previousStatus, timerInfo)
    case Timeout(t) => // ignore other timeouts
  }

  // We have a command status value, but no one has asked for it yet.
  // The command status could be updated again and/or we could get a request for it (completer).
  // If we timeout, quit (maybe nobody is interested in the command status?).
  private def waitingForCompleter(status: CommandStatus, timerInfo: TimerInfo, previouStatus: CommandStatus): Receive = {
    case completer: CommandStatusCompleter =>
      log.debug(s"Received completer (waiting for completer)")
      completeAndWait(completer, status, timerInfo)
    case status: CommandStatus =>
      log.debug(s"Received command status $status (waiting for completer)")
      context become waitingForCompleter(status, timerInfo, status)
    case timerInfo.`timeout` =>
      log.debug(s"Received timeout while waiting for completer: stopping")
      context.stop(self)
    case Timeout(t) => // ignore other timeouts
  }

  // Returns a new timer with an incremented id
  private def newTimeout(timerInfo: TimerInfo): TimerInfo = {
    timerInfo.timer.cancel()
    scheduleTimeout(timerInfo.timeout.copy(timerInfo.timeout.id + 1))
  }

  // Schedule the given timeout and return the timer info
  private def scheduleTimeout(timeout: Timeout): TimerInfo = {
    val timer = context.system.scheduler.scheduleOnce(timeoutDuration, self, timeout)
    TimerInfo(timeout, timer)
  }

  // Complete the request and quit if the status indicates that the command has completed or is
  // otherwise done (was cancelled or had an error).
  private def completeAndWait(completer: CommandStatusCompleter, status: CommandStatus, timerInfo: TimerInfo): Unit = {
    log.debug(s"Completing with status $status")
    completer(Some(status))
    if (status.done) {
      // We're done
      log.debug("Done, stopping self")
      context.stop(self)
      timerInfo.timer.cancel()
    } else {
      // Still waiting for the command to finish (status stays the same)
      log.debug(s"Continue waiting with status $status")
      context become waiting(newTimeout(timerInfo), status)
    }
  }
}
