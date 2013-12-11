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

  /**
   * True if the config execution should stop in this state (aborted, canceled, paused)
   */
  def stop : Boolean = false

  /**
   * Optional message for the Error status
   */
  def message: String = ""

  /**
   * Returns the same config state type with a different runId (XXX needed?)
   */
  def withRunId(runId: RunId) : CommandStatus
}

/**
 * Command status
 */
object CommandStatus {

  case class Submitted(runId: RunId) extends CommandStatus {
    override val done = false
    def withRunId(newRunId: RunId): Submitted = Submitted(newRunId)
  }

  case class Pending(runId: RunId) extends CommandStatus {
    override val done = false
    def withRunId(newRunId: RunId): Pending = Pending(newRunId)
  }

  case class Queued(runId: RunId) extends CommandStatus {
    override val done = false
    def withRunId(newRunId: RunId): Queued = Queued(newRunId)
  }

  case class Busy(runId: RunId) extends CommandStatus {
    override val done = false
    def withRunId(newRunId: RunId): Busy = Busy(newRunId)
  }

  case class Paused(runId: RunId) extends CommandStatus {
    override val done = false
    override val stop = true
    def withRunId(newRunId: RunId): Paused = Paused(newRunId)
  }

  case class Resumed(runId: RunId) extends CommandStatus {
    override val done = false
    def withRunId(newRunId: RunId): Resumed = Resumed(newRunId)
  }

  case class Completed(runId: RunId) extends CommandStatus {
    def withRunId(newRunId: RunId): Completed = Completed(newRunId)
  }

  case class Error(runId: RunId, msg: String) extends CommandStatus {
    override val message: String = msg
    def withRunId(newRunId: RunId): Error = Error(newRunId, msg)
  }

  case class Aborted(runId: RunId) extends CommandStatus {
    def withRunId(newRunId: RunId): Aborted = Aborted(newRunId)
  }

  case class Canceled(runId: RunId) extends CommandStatus {
    def withRunId(newRunId: RunId): Canceled = Canceled(newRunId)
  }


  /**
   * Creates a command status by name
   * @param name simple name of the command status class
   * @param runId the runId to pass to the constructor
   * @param message optional error message for the Error status
   * @return the command status object with the given fields
   */
  def apply(name: String, runId: RunId, message: String = ""): CommandStatus = name match {
    case "Submitted" => Submitted(runId)
    case "Pending" => Pending(runId)
    case "Queued" => Queued(runId)
    case "Busy" => Busy(runId)
    case "Paused" => Paused(runId)
    case "Resumed" => Resumed(runId)
    case "Completed" => Completed(runId)
    case "Error" => Error(runId, message)
    case "Aborted" => Aborted(runId)
    case "Canceled" => Canceled(runId)
  }
}
