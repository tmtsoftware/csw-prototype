package csw.services.cmd_old.akka

import akka.actor.{ Cancellable, ActorLogging, Props }
import akka.stream.actor.ActorPublisher
import csw.shared.cmd_old.CommandStatus
import csw.shared.cmd_old.RunId
import scala.collection.immutable.Queue
import scala.concurrent.duration.FiniteDuration
import akka.stream.actor.ActorPublisherMessage._

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
 * Waits for command status messages from a command service and publishes them to the akka stream.
 *
 * @param timeoutDuration automatically timeout requests for command status after this amount of time
 * @param runId the runId for the command being monitored
 */
final class CommandServiceStatusMonitor(timeoutDuration: FiniteDuration, runId: RunId)
    extends ActorPublisher[CommandStatus] with ActorLogging {

  import CommandServiceStatusMonitor._
  import context.dispatcher

  // Used to save status messages that have not been requested yet
  var queue = Queue[CommandStatus]()

  def receive: Receive =
    waiting(scheduleTimeout(Timeout(0)))

  // Wait for the command status, then publish it to the stream.
  // Give up if nothing happens in the required time.
  // If nobody has requested the status value yet, queue it up for later requests.
  private def waiting(timerInfo: TimerInfo): Receive = {
    case status: CommandStatus ⇒
      if (isActive && totalDemand > 0) {
        onNext(status)
        log.debug(s"Received command status $status (forwarded)")
      } else {
        queue = queue.enqueue(status)
        log.debug(s"Received command status $status (queued)")
      }
      if (status.done) {
        log.debug("Done, stopping self")
        context.stop(self)
      }
    case timerInfo.`timeout` ⇒
      log.warning(s"Received timeout while waiting for status of runId $runId: stopping")
      context.stop(self)

    case Timeout(t) ⇒ // ignore other timeouts

    case Request(_) ⇒ // request for next status item
      if (totalDemand > 0)
        queue.dequeueOption.foreach {
          case (status, q) ⇒
            queue = q
            onNext(status)
        }

    case Cancel ⇒
      log.debug("Cancel, stopping self")
      context.stop(self)
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
}
