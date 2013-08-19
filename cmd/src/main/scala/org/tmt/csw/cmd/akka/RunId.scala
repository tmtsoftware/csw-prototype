package org.tmt.csw.cmd.akka

import java.util.UUID

/**
 * Unique id for each running command (returned from a queue submit).
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
  override def toString: String = id
}

