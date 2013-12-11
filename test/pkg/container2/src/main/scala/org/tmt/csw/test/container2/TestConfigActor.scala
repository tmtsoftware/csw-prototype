package org.tmt.csw.test.container2

import akka.actor._
import org.tmt.csw.cmd.akka.{CommandStatusActor, CommandStatus, ConfigActor, RunId}
import akka.zeromq._
import akka.zeromq.Listener
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import akka.util.ByteString
import org.tmt.csw.cmd.core.Configuration
import scala.util.Success
import org.tmt.csw.cmd.akka.ConfigActor._

object TestConfigActor {
  def props(commandStatusActor: ActorRef, numberOfSecondsToRun: Int = 2): Props =
    Props(classOf[TestConfigActor], commandStatusActor, numberOfSecondsToRun)
}

/**
 * A test config actor (simulates an actor that does the work of executing a configuration).
 *
 * @param commandStatusActor actor that receives the command status messages
 * @param numberOfSecondsToRun the number of seconds to run the simulated work
 */
class TestConfigActor(override val commandStatusActor: ActorRef, numberOfSecondsToRun: Int) extends ConfigActor {

  val clientSocket = ZeroMQExtension(context.system).newSocket(SocketType.Req,
    Listener(self), Connect("tcp://127.0.0.1:6565")) // XXX TODO make host and port configurable

  // XXX temp: change to get values over ZMQ from hardware simulation
  var savedConfig: Option[Configuration] = None


  // Receive config messages
  def waitingForConfig: Receive = receiveConfigs

  // Don't want any new configs until we are done with the current one
  def noConfigs: Receive = {
    case s: SubmitWithRunId => submitError(s)
  }

  // Wait for hardware status after sending a hardware command
  def waitingForStatus(submit: SubmitWithRunId): Receive = noConfigs orElse receiveConfigs orElse {
    case Connecting ⇒ log.info("Connecting to hardware via ZMQ")
    case m: ZMQMessage ⇒ hardwareMessageReceived(m, submit)
    case x ⇒ log.info(s"ZMQ Unknown Message: $x")
  }

  // Receive
  override def receive: Receive = waitingForConfig

  // Called when a ØMQ message is received from the low level hardware server
  def hardwareMessageReceived(m: ZMQMessage, submit: SubmitWithRunId): Unit = {
    val msg = m.frame(0).utf8String
    log.info(s"ZMQ Message: $msg")
    val status = if (msg == "OK") {
      CommandStatus.Completed(submit.runId)
    } else {
      CommandStatus.Error(submit.runId, msg)
    }
    returnStatus(status, submit.submitter)
    context.become(waitingForConfig)
  }

  /**
   * Called when a configuration is submitted while we a are waiting for the last one
   * (an error in this case, assuming we want only one at a time)
   */
  def submitError(submit: SubmitWithRunId): Unit = {
    val status = CommandStatus.Error(submit.runId, "Received config before completing the last one")
    commandStatusActor ! CommandStatusActor.StatusUpdate(status, submit.submitter)
  }

  /**
   * Called when a configuration is submitted
   */
  override def submit(submit: SubmitWithRunId): Unit = {
    // Save the config for this test, so that query can return it later
    savedConfig = Some(submit.config)
    log.info("XXX sending dummy message to hardware")
    clientSocket ! ZMQMessage(ByteString("Dummy Message from Akka"))
    context.become(waitingForStatus(submit))
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  override def pause(runId: RunId): Unit = {
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  override def resume(runId: RunId): Unit = {
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  override def cancel(runId: RunId): Unit = {
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  override def abort(runId: RunId): Unit = {
  }

  /**
   * Query the current state of a device and reply to the sender with a ConfigResponse object.
   * A config is passed in (the values are ignored) and the reply will be sent containing the
   * same config with the current values filled out.
   *
   * @param config used to specify the keys for the values that should be returned
   * @param replyTo reply to this actor with the config response
   *
   */
  override def query(config: Configuration, replyTo: ActorRef): Unit = {
    // XXX TODO: replace savedConfig and get values over ZMQ from hardware simulation
    val conf = savedConfig match {
      // XXX TODO: should only fill in the values that are passed in!
      case Some(c)  => c
      case None =>
        if (config.hasPath("posName")) {
          config.
            withValue("posName", "NGC738B").
            withValue("c1", "22:35:58.530").
            withValue("c2", "33:57:55.40").
            withValue("equinox", "J2000")
        } else {
          config.
            withValue("c1", "22:35:01.066").
            withValue("c2", "33:58:21.69").
            withValue("equinox", "J2000")
        }
    }

    sender ! ConfigResponse(Success(conf))
  }
}

