package csw.shared.cmd


/**
 * The status of a running command
 */
sealed trait CommandStatus {

  /**
   * The unique id for the command
   */
  def runId: RunId

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

  case class Completed(runId: RunId) extends CommandStatus

  case class Error(runId: RunId, override val message: String) extends CommandStatus

  case class Aborted(runId: RunId) extends CommandStatus

  case class Canceled(runId: RunId) extends CommandStatus

}
