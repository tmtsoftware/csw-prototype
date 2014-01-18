package org.tmt.csw.test.container1

import akka.actor.Props
import org.tmt.csw.pkg.Assembly
import org.tmt.csw.cmd.akka.OneAtATimeCommandQueueController
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import java.util.Date

object Assembly1 {
  def props(name: String): Props = Props(classOf[Assembly1], name)
}

// A test assembly
case class Assembly1(name: String) extends Assembly with OneAtATimeCommandQueueController {

  def receive: Receive = receiveAssemblyMessages

  /**
   * Called when a command is submitted
   * @param s holds the config, runId and sender
   */
  override def submit(s: SubmitWithRunId): Unit = {
    log.info(s"Submit with runId(${s.runId}) ${s.config}")

    // Test changing the contents of the config
    val config = if (s.config.hasPath("config.tmt.mobie.blue.filter.value")) {
      s.config.withValue("config.tmt.mobie.blue.filter.timestamp", new Date().getTime)
    } else {
      s.config
    }
    commandQueueActor ! SubmitWithRunId(config, s.submitter, s.runId)
  }


  def initialize(): Unit = {log.info("Assembly1 initialize")}

  def startup(): Unit = {log.info("Assembly1 startup")}

  def run(): Unit = {log.info("Assembly1 run")}

  def shutdown(): Unit = {log.info("Assembly1 shutdown")}

  def uninit(): Unit = {log.info("Assembly1 uninit")}

  def remove(): Unit = {log.info("Assembly1 remove")}
}
