package org.tmt.csw.cmd.akka

import java.util.concurrent.atomic.AtomicReference
import org.tmt.csw.cmd.akka.CommandServiceMessage.SubmitWithRunId
import scala.concurrent.Future

/**
 * A test config worker actor.
 */
class TestConfigActorWorker() extends ConfigActor(Set[String]()) {
  // Used as an example of one way to implement interrupting a running config
  val aState: AtomicReference[ConfigState] = new AtomicReference(null)

  // Saved position and config for Pause, so that we can continue on Resume
  var savedPos = 1

  // Needed to implement the "resume" message
  var savedSubmit: SubmitWithRunId = null

  // The number of seconds to run when a config is submitted
  val numberOfSecondsToRun = 1

  /**
   * Called when a configuration is submitted
   */
  def submit(submit: SubmitWithRunId): Unit = {
    savedSubmit = submit
    aState.set(ConfigState.Submitted(submit.runId))
    doSubmit(submit)
  }

  def doSubmit(submit: SubmitWithRunId): Unit = {
    log.info(s"Processing config: ${submit.config}, reply when complete to ${submit.submitter}")
    implicit val dispatcher = context.system.dispatcher
    for {
      state <- Future {
        doWork(submit)
      } recover {
        case ex: Exception => ConfigState.Error(submit.runId)
      }
    } {
      if (state != ConfigState.Paused(submit.runId)) {
        submit.submitter ! state
        context.system.stop(self)
      }
    }
  }

  // Do some work (sleeping in a loop), and check for state changes
  def doWork(submit: SubmitWithRunId): ConfigState = {
    for (a <- savedPos to numberOfSecondsToRun*10) {
      // Check if we should stop
      val state = aState.get
      if (state.stop()) {
        if (state == ConfigState.Paused(submit.runId)) savePos(a)
        return state // Return the state to the sender
      } else {
        // Continue working
        log.info(s"${self.path} busy working on part $a of $numberOfSecondsToRun")
        Thread.sleep(100) // do some work...
      }
    }
    // Send the config state back to the original sender
    aState.get() match {
      case ConfigState.Submitted(runId) => ConfigState.Completed(submit.runId)
      case ConfigState.Resumed(runId) => ConfigState.Completed(submit.runId)
      case other => other // may have been aborted or canceled
    }
  }

  // Save the current position so we can resume processing later (when resume is called)
  def savePos(a: Int): Unit = {
    savedPos = a
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  def pause(runId: RunId): Unit = {
    aState.set(ConfigState.Paused(runId))
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  def resume(runId: RunId): Unit = {
    aState.set(ConfigState.Resumed(runId))
    doSubmit(savedSubmit)
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  def cancel(runId: RunId): Unit = {
    aState.set(ConfigState.Canceled(runId))
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  def abort(runId: RunId): Unit = {
    aState.set(ConfigState.Aborted(runId))
  }
}
