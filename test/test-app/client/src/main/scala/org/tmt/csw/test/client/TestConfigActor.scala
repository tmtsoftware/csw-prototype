package org.tmt.csw.test.client

import akka.actor._
import org.tmt.csw.cmd.akka.{RunId, ConfigActor}
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import org.tmt.csw.cmd.akka.ConfigActor._
import org.tmt.csw.cmd.core.Configuration
import org.tmt.csw.cmd.akka.ConfigActor.ConfigResume
import org.tmt.csw.cmd.akka.ConfigActor.ConfigAbort
import org.tmt.csw.cmd.akka.ConfigActor.ConfigCancel
import org.tmt.csw.cmd.akka.ConfigActor.ConfigPause
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId
import scala.Some
import scala.util.Success

object TestConfigActor {
  def props(commandStatusActor: ActorRef): Props = Props(classOf[TestConfigActor], commandStatusActor)
}

/**
 * A test config actor.
 */
class TestConfigActor(override val commandStatusActor: ActorRef) extends ConfigActor {

  // Links the config worker actor to the runId for the config it is currently executing
  private var runIdForActorRef = Map[ActorRef, RunId]()
  private var actorRefForRunId = Map[RunId, ActorRef]()

  // Receive config messages
  override def receive: Receive = receiveConfigs

  /**
   * Called when a configuration is submitted
   */
  override def submit(submit: SubmitWithRunId): Unit = {
    val configWorkerActor = context.actorOf(TestConfigActorWorker.props(commandStatusActor, 1), "testConfigActorWorker")
    log.debug(s"Forwarding config ${submit.config} to worker $configWorkerActor")
    runIdForActorRef += (configWorkerActor -> submit.runId)
    actorRefForRunId += (submit.runId -> configWorkerActor)
    context.watch(configWorkerActor)
    configWorkerActor ! submit
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  override def pause(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigPause(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  override def resume(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigResume(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  override def cancel(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigCancel(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  override def abort(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigAbort(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  override def query(config: Configuration, replyTo: ActorRef): Unit = {
    replyTo ! ConfigResponse(Success(config)) // XXX dummy implementation
  }

  /**
   * Called when a child (worker) actor terminates
   */
  override def terminated(actorRef: ActorRef): Unit = {
    runIdForActorRef.get(actorRef) match {
      case Some(runId) => actorRefForRunId -= runId
      case None =>
    }
    runIdForActorRef -= actorRef
  }
}

