package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.core.Configuration
import akka.actor._
import akka.pattern.ask
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Test of an actor that executes configurations
 */
class TestConfigActor(numberOfSecondsToRun: Int) extends ConfigActor {

  val workerActor = context.actorOf(Props(new TestWorkerActor(numberOfSecondsToRun = 3)),
    name = "TestWorkerActor")

  /**
   * Return a reference to the worker actor that handles the ConfigSubmit message in the background
   * (so that this actor is always ready to receive other messages, such as ConfigPause).
   */
  override def getWorkerActor = workerActor

  /**
   * Actions due to a previous request should be stopped immediately without completing.
   */
  override def configAbort() {
    super.configAbort()
    workerActor ! Kill
  }
}


/**
 * Worker actor: does the work for the submitted config
 */
private class TestWorkerActor(numberOfSecondsToRun: Int) extends Actor with ActorLogging {
  def receive = {
    case config: Configuration => sender ! doSomeWork(config)
  }

  /**
   * Do the actual work to execute or match the configuration on the target.
   * @param config the configuration to execute
   */
  def doSomeWork(config: Configuration) : ConfigActor.ConfigState = {
    for (a <- 1 to numberOfSecondsToRun) {
      log.info(s"${self.path} busy working on part $a of $numberOfSecondsToRun")
      Thread.sleep(1000)
    }
    // This value will be sent back to the original sender
    ConfigActor.Completed()
  }
}
