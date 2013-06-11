package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import com.typesafe.scalalogging.slf4j.Logging
import akka.actor.ActorLogging

/**
 */
class TestConfigActor(name: String, numberOfSecondsToRun: Int) extends ConfigActor(name) with ActorLogging {

  /**
   * Submits the given configuration
   * @param runId identifies the configuration
   * @param config the configuration to execute
   */
  override def configSubmit(runId: RunId, config: Configuration) {
    log.info(name + ": configSubmit: " + config.toString())
    for (a <- 1 to numberOfSecondsToRun) {
      println(name + " busy...")
      Thread.sleep(1000)
    }
  }
}

