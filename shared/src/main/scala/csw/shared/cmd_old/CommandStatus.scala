package csw.shared.cmd_old

/**
 * The status of a running command
 */
sealed trait CommandStatus {

  /**
   * The unique id for the command
   */
  def runId: RunId

  /**
   * True if the command is done (aborted, canceled, completed)
   */
  def done: Boolean = false

  /**
   * True if the command has partially completed
   */
  def partiallyDone: Boolean = false

  /**
   * Optional partial status name
   */
  def partialStatus: String = ""

  /**
   * True if the command execution should stop in this state (aborted, canceled, paused)
   */
  def stop: Boolean = false

  /**
   * Optional error message (or path, for PartiallyCompleted)
   */
  def message: String = ""

  /**
   * Returns the same config state type with a different runId
   */
  def withRunId(runId: RunId): CommandStatus

  /**
   * Returns a name for the status
   */
  def name: String = this.getClass.getSimpleName.toLowerCase
}

/**
 * Command status
 */
object CommandStatus {

  case class Submitted(runId: RunId) extends CommandStatus {
    override def withRunId(newRunId: RunId): Submitted = Submitted(newRunId)
  }

  case class Pending(runId: RunId) extends CommandStatus {
    override def withRunId(newRunId: RunId): Pending = Pending(newRunId)
  }

  case class Queued(runId: RunId) extends CommandStatus {
    override def withRunId(newRunId: RunId): Queued = Queued(newRunId)
  }

  case class Busy(runId: RunId) extends CommandStatus {
    override def withRunId(newRunId: RunId): Busy = Busy(newRunId)
  }

  case class Paused(runId: RunId) extends CommandStatus {
    override val stop = true

    override def withRunId(newRunId: RunId): Paused = Paused(newRunId)
  }

  case class Resumed(runId: RunId) extends CommandStatus {
    override def withRunId(newRunId: RunId): Resumed = Resumed(newRunId)
  }

  // One part of a config is complete (see ConfigDistributorActor)
  case class PartiallyCompleted(runId: RunId, path: Option[String], status: String) extends CommandStatus {
    override val partiallyDone = true
    override val message = if (path.isEmpty) "" else path.get

    override def withRunId(newRunId: RunId): PartiallyCompleted = PartiallyCompleted(newRunId, path, status)

    override def name = "partially completed"

    override def partialStatus = status
  }

  case class Completed(runId: RunId) extends CommandStatus {
    override val done = true

    override def withRunId(newRunId: RunId): Completed = Completed(newRunId)
  }

  case class Error(runId: RunId, msg: String) extends CommandStatus {
    override val stop = true
    override val done = true
    override val message = msg

    override def withRunId(newRunId: RunId): Error = Error(newRunId, msg)
  }

  case class Aborted(runId: RunId) extends CommandStatus {
    override val stop = true
    override val done = true

    override def withRunId(newRunId: RunId): Aborted = Aborted(newRunId)
  }

  case class Canceled(runId: RunId) extends CommandStatus {
    override val stop = true
    override val done = true

    override def withRunId(newRunId: RunId): Canceled = Canceled(newRunId)
  }

  /**
   * Creates a command status by name
   * @param name simple name of the command status class
   * @param runId the runId to pass to the constructor
   * @param message error message for the Error status, path for the partial status, or empty
   * @param partialStatus partial status value (for PartiallyCompleted) or empty
   * @return the command status object with the given fields
   */
  def apply(name: String, runId: RunId, message: String, partialStatus: String): CommandStatus =
    name match {
      case "submitted"           ⇒ Submitted(runId)
      case "pending"             ⇒ Pending(runId)
      case "queued"              ⇒ Queued(runId)
      case "busy"                ⇒ Busy(runId)
      case "paused"              ⇒ Paused(runId)
      case "resumed"             ⇒ Resumed(runId)
      case "partially completed" ⇒ PartiallyCompleted(runId, Some(message), partialStatus)
      case "completed"           ⇒ Completed(runId)
      case "error"               ⇒ Error(runId, message)
      case "aborted"             ⇒ Aborted(runId)
      case "canceled"            ⇒ Canceled(runId)
    }
}
