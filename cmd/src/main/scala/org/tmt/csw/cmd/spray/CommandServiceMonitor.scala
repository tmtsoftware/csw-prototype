package org.tmt.csw.cmd.spray

import akka.actor.{ActorLogging, Actor, Props}
import scala.concurrent.duration.FiniteDuration
import org.tmt.csw.cmd.akka.CommandStatus

object CommandServiceMonitor {

  private case class Timeout(id: Int)

  def props(timeout: FiniteDuration): Props =
    Props(new CommandServiceMonitor(timeout))
}

/**
 * Implements long polling requests on the command service actor.
 * (Original algorithm based on Spray example at http://hseeberger.github.io/blog/2013/07/22/gabbler-part3/)
 */
final class CommandServiceMonitor(timeoutDuration: FiniteDuration) extends Actor with ActorLogging {

  import CommandServiceMonitor._
  import CommandService._
  import context.dispatcher

  def receive: Receive =
    waiting(scheduleTimeout(Timeout(0)))

  // Wait for the command status or for a request for it (completer).
  // Give up if nothing happens in the required time.
  private def waiting(timeout: Timeout): Receive = {
    case completer: Completer =>
      log.debug(s"waiting: received completer: $completer")
      context become waitingForStatus(completer, newTimeout(timeout))
    case status: CommandStatus =>
      log.debug(s"waiting: received status: $status")
      context become waitingForCompleter(status, timeout)
    case `timeout` =>
      log.debug("waiting: received timeout")
      context.stop(self)
  }

  // We have a request for command status (completer), but no status value to return yet.
  // Wait for a command status message to arrive and if we timeout, return None for the status.
  // The requester should then try again later.
  private def waitingForStatus(completer: Completer, timeout: Timeout): Receive = {
    case completer: Completer =>
      log.debug(s"waitingForStatus: received completer: $completer")
      context become waitingForStatus(completer, newTimeout(timeout))
    case status: CommandStatus =>
      log.debug(s"waitingForStatus: received status: $status")
      completeAndWait(completer, Some(status), timeout)
    case `timeout` =>
      log.debug("waitingForStatus: received timeout")
      completeAndWait(completer, None, timeout)
  }

  // We have a command status value, but no one has asked for it yet.
  // The command status could be updated again and/or we could get a request for it (completer).
  // If we timeout, quit (maybe nobody is interested in the command status?).
  private def waitingForCompleter(status: CommandStatus, timeout: Timeout): Receive = {
    case completer: Completer =>
      log.debug(s"waitingForCompleter: received completer: $completer")
      completeAndWait(completer, Some(status), timeout)
    case status: CommandStatus =>
      log.debug(s"waitingForCompleter: received status: $status")
      context become waitingForCompleter(status, timeout)
    case `timeout` =>
      log.debug("waitingForCompleter: received timeout")
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
  private def completeAndWait(completer: Completer, status: Option[CommandStatus], timeout: Timeout): Unit = {
    log.debug(s"completeAndWait: status: $status")
    completer(status)
    if (!status.isEmpty && status.get.done) {
      // We're done or timed out, so quit. If status was empty, the requester can try again later.
      log.debug(s"completeAndWait: stopping")
      context.stop(self)
    } else {
      // Still waiting for the command to finish
      context become waiting(newTimeout(timeout))
    }
  }
}
