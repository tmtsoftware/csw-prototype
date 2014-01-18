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
import com.typesafe.config.ConfigFactory

object TestConfigActor {
  def props(commandStatusActor: ActorRef, configKey: String, numberOfSecondsToRun: Int = 2): Props =
    Props(classOf[TestConfigActor], commandStatusActor, configKey, numberOfSecondsToRun)

  val config = ConfigFactory.load("TestConfigActor")
}

/**
 * A test config actor (simulates an actor that does the work of executing a configuration).
 *
 * @param commandStatusActor actor that receives the command status messages
 * @param numberOfSecondsToRun the number of seconds to run the simulated work
 * @param configKey set to the last component of a key, for example: "filter" or "disperser" in this test
 */
class TestConfigActor(override val commandStatusActor: ActorRef, configKey: String,
                      numberOfSecondsToRun: Int) extends ConfigActor {

  val url = TestConfigActor.config.getString(s"TestConfigActor.$configKey.url")
  log.info(s"For $configKey: using ZMQ URL = $url")

  val clientSocket = ZeroMQExtension(context.system).newSocket(SocketType.Req,
    Listener(self), Connect(url))

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
    log.info("Sending dummy message to ØMQ hardware simulation")

    // Note: We could just send the XML and let the C code parse it, but for now, keep it simple
    // and extract the value here
    val value = configKey match {
      case "filter" | "disperser" => submit.config.getString("value")
      case "pos" | "one" =>
        val c1 = submit.config.getString("c1")
        val c2 = submit.config.getString("c2")
        val equinox = submit.config.getString("equinox")
        s"$c1 $c2 $equinox"
      case _ => "error"
    }

    // For this test, a timestamp value is inserted by assembly1 (Later the XML can be just passed on to ZMQ)
    val zmqMsg = configKey match {
      case "filter" =>
        val timestamp = submit.config.getString("timestamp")
        ByteString(s"$configKey=$value, timestamp=$timestamp")
      case _ =>
        ByteString(s"$configKey=$value")
    }

    clientSocket ! ZMQMessage(zmqMsg)
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
      case Some(c)  => c
      case None =>
        if (configKey == "filter") {
          config.withValue("value", "None")
        } else if (configKey == "disperser") {
          config.withValue("value", "Mirror")
        } else if (configKey == "pos") {
          config.withValue("posName", "m653").withValue("c1", "03:19:34.2").withValue("c2", "31:23:21.5").withValue("equinox", "J2000")
        } else if (configKey == "one") {
          config.withValue("c1", "03:20:29.2").withValue("c2", "31:24:02.1").withValue("equinox", "J2000")
        } else config
    }

    sender ! ConfigResponse(Success(conf))
  }
}

