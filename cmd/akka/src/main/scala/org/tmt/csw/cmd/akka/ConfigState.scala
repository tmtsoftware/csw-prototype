package org.tmt.csw.cmd.akka

object ConfigState {
  /**
   * Configuration execution states
   */
  sealed trait ConfigState {
    /**
     * Returns true if the action should stop
     */
    def stop() : Boolean
  }

  case class Initialized() extends ConfigState {def stop() = false}
  case class Submitted() extends ConfigState {def stop() = false}
  case class Canceled() extends ConfigState {def stop() = true}
  case class Aborted() extends ConfigState {def stop() = true}
  case class Paused() extends ConfigState {def stop() = true}
  case class Resumed() extends ConfigState {def stop() = false}
  case class Completed() extends ConfigState {def stop() = false}
}
