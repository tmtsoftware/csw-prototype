package org.tmt.csw.test.container2

import akka.actor._
import org.tmt.csw.cmd.akka.ConfigActor._
import org.tmt.csw.cmd.akka.CommandQueueActor._
import org.tmt.csw.cmd.akka.{ConfigActor, RunId}

object TestConfigActor {
  def props(commandStatusActor: ActorRef, configPath: String, numberOfSecondsToRun: Int = 2): Props =
    Props(classOf[TestConfigActor], commandStatusActor, configPath, numberOfSecondsToRun)
}

/**
 * A test config actor (simulates an actor that does the work of executing a configuration).
 *
 * @param commandStatusActor actor that receives the command status messages
 * @param configPath a dot-separated configuration key path: This actor will receive the parts
 *                    of configs containing any of these paths
 * @param numberOfSecondsToRun the number of seconds to run the simulated work
 */
class TestConfigActor(override val commandStatusActor: ActorRef, configPath: String, numberOfSecondsToRun: Int) extends ConfigActor {

  // Links the config worker actor to the runId for the config it is currently executing
  private var runIdForActorRef = Map[ActorRef, RunId]()
  private var actorRefForRunId = Map[RunId, ActorRef]()

  // Receive config messages
  override def receive: Receive = receiveConfigs

  // The set of config paths we will process
  override val configPaths = Set(configPath)

  /**
   * Called when a configuration is submitted
   */
  def submit(submit: SubmitWithRunId): Unit = {
    val configWorkerActor = context.actorOf(TestConfigActorWorker.props(commandStatusActor, numberOfSecondsToRun), "testConfigActorWorker")
    log.debug(s"Forwarding config ${submit.config} to worker $configWorkerActor")
    runIdForActorRef += (configWorkerActor -> submit.runId)
    actorRefForRunId += (submit.runId -> configWorkerActor)
    context.watch(configWorkerActor)
    configWorkerActor ! submit
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  def pause(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigPause(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  def resume(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigResume(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  def cancel(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigCancel(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  def abort(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigAbort(runId)
      case None => log.error(s"No worker actor found for runId: $runId")
    }
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

