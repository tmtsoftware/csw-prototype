package org.tmt.csw.cmd.akka

/**
 * Configuration execution states
 */
sealed trait ConfigState {

  /**
   * The run id assigned to this config when it was submitted
   */
  def runId(): RunId

  /**
   * Returns true if the action should stop
   */
  def stop() : Boolean

  /**
   * Returns true if the action is done (completed, aborted, canceled)
   */
  def done() : Boolean

  /**
   * Returns the same config state type with a different runId
   */
  def withRunId(runId: RunId) : ConfigState
}

object ConfigState {

  case class Submitted(runId: RunId) extends ConfigState {
    def stop() = false
    def done() = false
    def withRunId(newRunId: RunId): Submitted = Submitted(newRunId)
  }

  case class Canceled(runId: RunId) extends ConfigState {
    def stop() = true
    def done() = true
    def withRunId(newRunId: RunId): Canceled = Canceled(newRunId)
  }

  case class Aborted(runId: RunId) extends ConfigState {
    def stop() = true
    def done() = true
    def withRunId(newRunId: RunId): Aborted = Aborted(newRunId)
  }

  case class Paused(runId: RunId) extends ConfigState {
    def stop() = true
    def done() = false
    def withRunId(newRunId: RunId): Paused = Paused(newRunId)
  }

  case class Resumed(runId: RunId) extends ConfigState {
    def stop() = false
    def done() = false
    def withRunId(newRunId: RunId): Resumed = Resumed(newRunId)
  }

  case class Completed(runId: RunId) extends ConfigState {
    def stop() = false
    def done() = true
    def withRunId(newRunId: RunId): Completed = Completed(newRunId)
  }

  case class Error(runId: RunId) extends ConfigState {
    def stop() = false
    def done() = true
    def withRunId(newRunId: RunId): Error = Error(newRunId)
  }
}
