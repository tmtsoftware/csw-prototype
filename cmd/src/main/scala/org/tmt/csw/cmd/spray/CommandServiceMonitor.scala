package org.tmt.csw.cmd.spray

import akka.actor.{ActorLogging, Actor, Props}
import scala.concurrent.duration.FiniteDuration
import org.tmt.csw.cmd.akka.{RunId, CommandStatus}

object CommandServiceMonitor {

  private case class Timeout(id: Int)

  def props(timeout: FiniteDuration, runId: RunId): Props =
    Props(new CommandServiceMonitor(timeout, runId))
}

/**
 * Implements long polling requests on the command service actor.
 * (Original algorithm based on Spray example at http://hseeberger.github.io/blog/2013/07/22/gabbler-part3/)
 */
final class CommandServiceMonitor(timeoutDuration: FiniteDuration, runId: RunId) extends Actor with ActorLogging {

  import CommandServiceMonitor._
  import CommandService._
  import context.dispatcher

  def receive: Receive =
    waiting(scheduleTimeout(Timeout(0)), CommandStatus.Pending(runId).asInstanceOf[CommandStatus])

  // Wait for the command status or for a request for it (completer).
  // Give up if nothing happens in the required time.
  private def waiting(timeout: Timeout, previouStatus: CommandStatus): Receive = {
    case completer: Completer =>
      log.info(s"Received completer (waiting)")
      context become waitingForStatus(completer, newTimeout(timeout), previouStatus)
    case status: CommandStatus =>
      log.info(s"Received command status $status (waiting)")
      context become waitingForCompleter(status, timeout, status)
    case `timeout` =>
      log.info(s"Received timeout while waiting: stopping")
      context.stop(self)
  }

  // We have a request for command status (completer), but no status value to return yet.
  // Wait for a command status message to arrive and if we timeout, return None for the status.
  // The requester should then try again later.
  private def waitingForStatus(completer: Completer, timeout: Timeout, previousStatus: CommandStatus): Receive = {
    case completer: Completer =>
      log.info(s"Received completer (waiting for status)")
      context become waitingForStatus(completer, newTimeout(timeout), previousStatus)
    case status: CommandStatus =>
      log.info(s"Received command status $status (waiting for status)")
      completeAndWait(completer, status, timeout)
    case `timeout` =>
      log.info(s"Received timeout while waiting for command status: completing")
      completeAndWait(completer, previousStatus, timeout)
  }

  // We have a command status value, but no one has asked for it yet.
  // The command status could be updated again and/or we could get a request for it (completer).
  // If we timeout, quit (maybe nobody is interested in the command status?).
  private def waitingForCompleter(status: CommandStatus, timeout: Timeout, previouStatus: CommandStatus): Receive = {
    case completer: Completer =>
      log.info(s"Received completer (waiting for completer)")
      completeAndWait(completer, status, timeout)
    case status: CommandStatus =>
      log.info(s"Received command status $status (waiting for completer)")
      context become waitingForCompleter(status, timeout, status)
    case `timeout` =>
      log.info(s"Received timeout while waiting for completer: stopping")
      context.stop(self)
  }

  private def newTimeout(timeout: Timeout): Timeout =
    scheduleTimeout(timeout.copy(timeout.id + 1))

  private def scheduleTimeout(timeout: Timeout): Timeout = {
    context.system.scheduler.scheduleOnce(timeoutDuration, self, timeout)
    timeout
  }

  // Complete the HTTP request and quit if the status indicates that the command has completed or is
  // otherwise done (was cancelled or had an error).
  private def completeAndWait(completer: Completer, status: CommandStatus, timeout: Timeout): Unit = {
    log.info(s"Completing with status $status")
    completer(Some(status))
    if (status.done) {
      // We're done
      log.info("Done, stopping self")
      context.stop(self)
    } else {
      // Still waiting for the command to finish (status stays the same)
      log.info(s"Continue waiting with status $status")
      context become waiting(newTimeout(timeout), status)
    }
  }
}
