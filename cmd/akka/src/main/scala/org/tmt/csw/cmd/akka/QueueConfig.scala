package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration


/**
 * Combines a RunId with a Configuration object.
 * Objects of this type are placed in the command queue.
 */
case class QueueConfig(runId: RunId, config: Configuration) extends Comparable[QueueConfig] {
  def compareTo(that: QueueConfig): Int = this.runId.id.compareTo(that.runId.id)
}
