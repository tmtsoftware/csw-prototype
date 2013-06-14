package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import akka.actor._
import akka.pattern.ask
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cmd.akka.ConfigState.ConfigState

/**
 * Test of an actor that executes configurations
 */
object TestConfigActor {
  def props(numberOfSecondsToRun: Int) = Props(classOf[TestConfigActor], numberOfSecondsToRun: Int)
}

class TestConfigActor(numberOfSecondsToRun: Int) extends ConfigActor {

  val workerActor = context.actorOf(TestWorkerActor.props(3), name = "TestWorkerActor")

  /**
   * Return a reference to the worker actor that handles the ConfigSubmit message in the background
   * (so that this actor is always ready to receive other messages, such as ConfigPause).
   */
  override def getWorkerActor = workerActor

  /**
   * Actions due to a previous request should be stopped immediately without completing.
   */
  override def configAbort() {
    workerActor ! Kill
  }

  /**
   * Actions due to a Configuration should be stopped cleanly as soon as convenient without necessarily completing.
   */
  override def configCancel() {
  }

  /**
   * Pause the actions associated with a specific Configuration.
   */
  override def configPause() {
  }

  /**
   * Resume the paused actions associated with a specific Configuration.
   */
  override def configResume() {
  }

  override def postStop() {
    log.debug("TestConfigActor stopped")
  }
}


/**
 * Worker actor: does the work for the submitted config
 */
private object TestWorkerActor {
  def props(numberOfSecondsToRun: Int) = Props(classOf[TestWorkerActor], numberOfSecondsToRun)
}

private class TestWorkerActor(numberOfSecondsToRun: Int) extends Actor with ActorLogging {
  def receive = {
    case config:
      Configuration => sender ! doSomeWork(config)
      context.become(working)
  }

  /**
   * Do the actual work to execute or match the configuration on the target.
   * @param config the configuration to execute
   */
  def doSomeWork(config: Configuration): ConfigState = {
    for (a <- 1 to numberOfSecondsToRun) {
      log.info(s"${self.path} busy working on part $a of $numberOfSecondsToRun")
      Thread.sleep(1000)
    }
    // This value will be sent back to the original sender
    ConfigState.Completed
  }

  def working: Receive = {
    case config => log.error("Error: Received config while working: Should not happen.")
  }

  override def postStop() {
    log.debug("TestWorkerActor stopped")
  }
}
