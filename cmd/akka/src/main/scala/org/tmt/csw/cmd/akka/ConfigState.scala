package org.tmt.csw.cmd.akka

/**
 * Configuration execution states
 */
object ConfigState {
  sealed trait ConfigState
  case object Initialized extends ConfigState
  case object Submitted extends ConfigState
  case object Canceled extends ConfigState
  case object Aborted extends ConfigState
  case object Paused extends ConfigState
  case object Resumed extends ConfigState
  case object Completed extends ConfigState
}
