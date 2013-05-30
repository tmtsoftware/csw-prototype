package org.tmt.csw.cmd

import java.util.UUID

/**
 * Token returned from a command queue submit
 */
sealed trait RunId {
  def id : String
}

object RunId {
  def apply() : RunId = {
    new UuidRunId
  }
}

private case class UuidRunId(id : String) extends RunId {
  def this() = this(UUID.randomUUID().toString)
}

