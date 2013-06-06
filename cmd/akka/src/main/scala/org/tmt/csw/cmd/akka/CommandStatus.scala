package org.tmt.csw.cmd.akka

/**
 * Command Status Messages
 *
 * The movement of the Configuration through the execution states can generate
 * notification to the client (callback or event).
 */
sealed trait CommandStatus

object CommandStatus {
  case class StatusPending(runId: RunId) extends CommandStatus
  case class StatusQueued(runId: RunId) extends CommandStatus
  case class StatusBusy(runId: RunId) extends CommandStatus
  case class StatusComplete(runId: RunId) extends CommandStatus
  case class StatusError(runId: RunId) extends CommandStatus
  case class StatusAborted(runId: RunId) extends CommandStatus
}
