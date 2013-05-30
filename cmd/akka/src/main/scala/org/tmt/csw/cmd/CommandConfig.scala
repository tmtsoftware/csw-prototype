package org.tmt.csw.cmd

import com.typesafe.config.Config

/**
 * Combines a RunId with one or more Config object.
 * Objects of this type are placed in the command queue.
 */
case class CommandConfig(runId: RunId, configs: Seq[Config]) extends Comparable[CommandConfig] {
  def compareTo(that: CommandConfig): Int = this.runId.id.compareTo(that.runId.id)
}
