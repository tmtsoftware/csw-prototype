package org.tmt.csw.cmd.akka


/**
 * The status of a running command
 */
sealed trait CommandStatus {
  /**
   * The unique id for the command
   */
  def runId : RunId

  /**
   * True if the command is done (aborted, canceled, completed)
   */
  def done : Boolean = true
}

/**
 * Command status
 */
object CommandStatus {
  case class Pending(runId: RunId) extends CommandStatus {override def done = false}
  case class Queued(runId: RunId) extends CommandStatus {override def done = false}
  case class Busy(runId: RunId) extends CommandStatus {override def done = false}
  case class Complete(runId: RunId) extends CommandStatus
  case class Error(runId: RunId, ex: Throwable) extends CommandStatus
  case class Aborted(runId: RunId) extends CommandStatus
  case class Canceled(runId: RunId) extends CommandStatus
}
