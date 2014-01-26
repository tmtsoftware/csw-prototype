package org.tmt.csw.test.container2

import akka.actor._
import org.tmt.csw.cmd.akka.{CommandStatusActor, CommandStatus, ConfigActor, RunId}
import akka.zeromq._
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import akka.util.ByteString
import org.tmt.csw.cmd.core.Configuration
import scala.util.{Failure, Success}
import org.tmt.csw.cmd.akka.ConfigActor._
import com.typesafe.config.ConfigFactory
import akka.pattern.ask
import scala.concurrent.duration._

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

  val zmqClient = context.actorOf(ZmqClient.props(url))

  // XXX temp: change to get values over ZMQ from hardware simulation
  var savedConfig: Option[Configuration] = None


  // Receive
  override def receive: Receive = receiveConfigs

  /**
   * Called when a configuration is submitted
   */
  override def submit(submit: SubmitWithRunId): Unit = {
    // Save the config for this test, so that query can return it later
    savedConfig = Some(submit.config)
    log.info("Sending dummy message to ZMQ hardware simulation")

    // Note: We could just send the JSON and let the C code parse it, but for now, keep it simple
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

    // For this test, a timestamp value is inserted by assembly1 (Later the JSON can be just passed on to ZMQ)
    val zmqMsg = configKey match {
      case "filter" =>
        val timestamp = submit.config.getString("timestamp")
        ByteString(s"$configKey=$value, timestamp=$timestamp")
      case _ =>
        ByteString(s"$configKey=$value")
    }

    implicit val dispatcher = context.system.dispatcher
    ask(zmqClient, ZmqClient.Command(zmqMsg))(6 seconds) onComplete {

      case Success(ZMQMessage(frames)) =>
        val msg = frames(0).utf8String
        log.info(s"ZMQ Message: $msg")
        val status = if (msg == "OK") {
          CommandStatus.Completed(submit.runId)
        } else {
          CommandStatus.Error(submit.runId, msg)
        }
        returnStatus(status, submit.submitter)

      case Success(m) => // should not happen
        log.error(s"Unexpected ZMQ Message: $m")

      case Failure(ex) =>
        val status = CommandStatus.Error(submit.runId, ex.getMessage)
        commandStatusActor ! CommandStatusActor.StatusUpdate(status, submit.submitter)
    }
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
      case Some(c) => c
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

