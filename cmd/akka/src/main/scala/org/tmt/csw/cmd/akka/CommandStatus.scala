package org.tmt.csw.cmd.akka

/**
 * Command status
 */
object CommandStatus {
  sealed trait CommandStatus {
    def runId : RunId
    def done : Boolean = true
  }
  case class Pending(runId: RunId) extends CommandStatus {override def done = false}
  case class Queued(runId: RunId) extends CommandStatus {override def done = false}
  case class Busy(runId: RunId) extends CommandStatus {override def done = false}
  case class Complete(runId: RunId) extends CommandStatus
  case class Error(runId: RunId, ex: Throwable) extends CommandStatus
  case class Aborted(runId: RunId) extends CommandStatus
  case class Canceled(runId: RunId) extends CommandStatus
}
