package csw.shared.cmd

import java.util.UUID

/**
 * The status of a running command
 */
sealed trait CommandStatus {

  /**
   * The unique id for the command
   */
  def runId: UUID

  /**
   * Optional error message (or path, for PartiallyCompleted)
   */
  def message: String = ""

  /**
   * Returns a name for the status
   */
  def name: String = this.getClass.getSimpleName.toLowerCase
}

/**
 * Command status
 */
object CommandStatus {

  case class Completed(runId: UUID) extends CommandStatus

  case class Error(runId: UUID, override val message: String) extends CommandStatus

  case class Aborted(runId: UUID) extends CommandStatus

  case class Canceled(runId: UUID) extends CommandStatus

}
