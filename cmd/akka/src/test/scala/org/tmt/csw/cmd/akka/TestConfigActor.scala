package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.akka.CommandServiceMessage._
import akka.actor._

object TestConfigActor {
  def props(n: Int): Props = Props(classOf[TestConfigActor], n)
}

/**
 * A test config actor.
 * @param n When tests run concurrently, the actors being tested get unique names by appending -$n
 */
class TestConfigActor(n: Int) extends ConfigActor("config.tmt.tel.base.pos") {

  // Links the config worker actor to the runId for the config it is currently executing
  private var runIdForActorRef = Map[ActorRef, RunId]()
  private var actorRefForRunId = Map[RunId, ActorRef]()

  /**
   * Called when a configuration is submitted
   */
  def submit(submit: SubmitWithRunId) {
    val configWorkerActor = context.actorOf(Props[TestConfigActorWorker], "testConfigActorWorker")
    log.debug(s"Forwarding config ${submit.config} to worker $configWorkerActor")
    runIdForActorRef += (configWorkerActor -> submit.runId)
    actorRefForRunId += (submit.runId -> configWorkerActor)
    context.watch(configWorkerActor)
    configWorkerActor ! submit
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  def pause(runId: RunId) {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigPause(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  def resume(runId: RunId) {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigResume(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  def cancel(runId: RunId) {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigCancel(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  def abort(runId: RunId) {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigAbort(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Called when a child (worker) actor terminates
   */
  override def terminated(actorRef: ActorRef) {
    runIdForActorRef.get(actorRef) match {
      case Some(runId) => actorRefForRunId -= runId
      case None =>
    }
    runIdForActorRef -= actorRef
  }
}

