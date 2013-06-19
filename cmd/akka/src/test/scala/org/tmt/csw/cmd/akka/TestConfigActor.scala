package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import akka.actor._
import org.tmt.csw.cmd.akka.ConfigState.ConfigState
import java.util.concurrent.atomic.AtomicReference

/**
 * Test actor that executes configurations
 */
object TestConfigActor {
  /**
   * Used to create instances of this actor.
   */
  def props(numberOfSecondsToRun: Int) = Props(classOf[TestConfigActor], numberOfSecondsToRun: Int)
}

/**
 * A test config actor.
 * @param numberOfSecondsToRun Tells the actor how many seconds to run
 */
class TestConfigActor(numberOfSecondsToRun: Int) extends ConfigActor {

  // Saved position and config for Pause, so that we can continue on Resume
  var savedPos = 1

  /**
   * Execute the given config.
   * Implementations should monitor the state variable and stop work if needed,
   * due to a change of state to Aborted, Canceled or Paused.
   *
   * @param config the configuration to execute
   * @param aState the current configuration state
   */
  def submit(config: Configuration, aState: AtomicReference[ConfigState]) : ConfigState = {
    for (a <- savedPos to numberOfSecondsToRun) {
      // Check if we should stop
      val state = aState.get
      if (state.stop()) {
        if (state == ConfigState.Paused()) saveState(a)
        return state
      }

      // Continue working
      log.info(s"${self.path} busy working on part $a of $numberOfSecondsToRun")
      Thread.sleep(1000) // do some work...
    }

    // This value will be sent back to the original sender
    ConfigState.Completed()
  }

  // Save the current state so we can resume processing later (when resume is called)
  private def saveState(a: Int) {
    savedPos = a
  }

  /**
   * Resume the paused actions associated with a specific Configuration.
   */
  def resume(config: Configuration, state: AtomicReference[ConfigState]) : ConfigState = {
    submit(config, state)
  }
}
