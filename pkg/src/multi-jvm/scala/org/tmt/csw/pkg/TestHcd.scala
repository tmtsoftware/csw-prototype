package scala.org.tmt.csw.pkg

import org.tmt.csw.pkg.Hcd
import org.tmt.csw.cmd.akka.RunId
import org.tmt.csw.cmd.akka.CommandServiceMessage.SubmitWithRunId
import akka.actor.Props

object TestHcd {
  def props(name: String, configPaths: Set[String]): Props = Props(classOf[TestHcd], name, configPaths)
}

case class TestHcd(name: String, configPaths: Set[String]) extends Hcd {

  override def receive: Receive = receiveHcdMessages

  // -- Implement Component methods --
  override def initialize(): Unit = {
    log.info(s"$name initialize")
  }

  override def startup(): Unit = {
    log.info(s"$name startup")
  }

  override def run(): Unit = {
    log.info(s"$name run")
  }

  override def shutdown(): Unit = {
    log.info(s"$name shutdown")
  }

  override def uninit(): Unit = {
    log.info(s"$name uninit")
  }

  override def remove(): Unit = {
    log.info(s"$name remove")
  }

  // -- Implement ConfigActor methods --
  override def submit(submit: SubmitWithRunId): Unit = {
    log.info(s"$name submit: ${submit.config}")
  }

  override def pause(runId: RunId): Unit =  {
    log.info(s"$name pause")
  }

  override def resume(runId: RunId): Unit =  {
    log.info(s"$name resume")
  }

  override def cancel(runId: RunId): Unit =  {
    log.info(s"$name cancel")
  }

  override def abort(runId: RunId): Unit =  {
    log.info(s"$name abort")
  }
}
