package org.tmt.csw.cmd

import com.typesafe.config.Config

/**
 * Combines a RunId with one or more Config object.
 * Objects of this type are placed in the command queue.
 */
case class QueueConfig(runId: RunId, configs: Seq[Config]) extends Comparable[QueueConfig] {
  def compareTo(that: QueueConfig): Int = this.runId.id.compareTo(that.runId.id)
}
