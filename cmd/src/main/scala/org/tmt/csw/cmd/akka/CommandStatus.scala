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
   * Optional message for the Error status
   */
  def message: String = ""
}

/**
 * Command status
 */
object CommandStatus {
  case class Pending(runId: RunId) extends CommandStatus {override def done = false}
  case class Queued(runId: RunId) extends CommandStatus {override def done = false}
  case class Busy(runId: RunId) extends CommandStatus {override def done = false}
  case class Complete(runId: RunId) extends CommandStatus
  case class Error(runId: RunId, msg: String) extends CommandStatus {override def message: String = msg}
  case class Aborted(runId: RunId) extends CommandStatus
  case class Canceled(runId: RunId) extends CommandStatus


  /**
   * Creates a command status by name
   * @param name simple name of the command status class
   * @param runId the runId to pass to the constructor
   * @param message optional error message for the Error status
   * @return the command status object with the given fields
   */
  def apply(name: String, runId: RunId, message: String = ""): CommandStatus = name match {
    case "Pending" => Pending(runId)
    case "Queued" => Queued(runId)
    case "Busy" => Busy(runId)
    case "Complete" => Complete(runId)
    case "Error" => Error(runId, message)
    case "Aborted" => Aborted(runId)
    case "Canceled" => Canceled(runId)
  }
}
