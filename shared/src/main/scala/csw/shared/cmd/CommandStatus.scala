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

  /**
   * True for the Completed status
   */
  def isSuccess: Boolean = false

  /**
   * True if not the Completed status
   */
  def isFailed: Boolean = !isSuccess

  /**
    * True if execution of the command has stopped (because completed, canceled, aborted, error, etc.)
    */
  def isDone: Boolean = true
}

/**
 * Command status
 */
object CommandStatus {

  /**
    * The command has completed successfully
    */
  case class Completed(runId: RunId) extends CommandStatus {
    override def isSuccess: Boolean = true
  }

  /**
    * The command has been accepted (checked requirements, etc.)
    */
  case class Accepted(runId: RunId) extends CommandStatus {
    override def isDone: Boolean = false
  }

  /**
    * The command has been rejected because the assembly is busy (XXX is this needed?)
    */
  case class Busy(runId: RunId) extends CommandStatus

  /**
    * The command failed with the given message
    */
  case class Error(runId: RunId, override val message: String) extends CommandStatus

  /**
    * The command was aborted
    */
  case class Aborted(runId: RunId) extends CommandStatus

  /**
    * The command was canceled
    */
  case class Canceled(runId: RunId) extends CommandStatus
}
