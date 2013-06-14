package org.tmt.csw.cmd.akka

/**
 * Command status
 */
object CommandStatus {
  sealed trait CommandStatus
  case class Pending(runId: RunId) extends CommandStatus
  case class Queued(runId: RunId) extends CommandStatus
  case class Busy(runId: RunId) extends CommandStatus
  case class Complete(runId: RunId) extends CommandStatus
  case class Error(runId: RunId, ex: Throwable) extends CommandStatus
  case class Aborted(runId: RunId) extends CommandStatus
}
