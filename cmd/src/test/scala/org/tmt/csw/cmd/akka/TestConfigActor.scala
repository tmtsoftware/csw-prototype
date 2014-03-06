package org.tmt.csw.cmd.akka

import akka.actor._
import org.tmt.csw.cmd.akka.ConfigActor._
import org.tmt.csw.cmd.akka.CommandQueueActor._
import org.tmt.csw.cmd.core.Configuration
import scala.util.Success

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

  // Links the config worker actor to the runId for the config it is currently executing
  private var runIdForActorRef = Map[ActorRef, RunId]()
  private var actorRefForRunId = Map[RunId, ActorRef]()

  // XXX dummy config for test of get/query
  private var savedConfig: Option[Configuration] = None


  // Receive config messages
  override def receive: Receive = receiveConfigs

  /**
   * Called when a configuration is submitted
   */
  override def submit(submit: SubmitWithRunId): Unit = {
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
  override def pause(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigPause(runId)
      case None => log.error(s"Can't pause config: No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  override def resume(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigResume(runId)
      case None => log.error(s"Can't resume config: No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  override def cancel(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigCancel(runId)
      case None => log.error(s"Can't cancel config: No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  override def abort(runId: RunId): Unit = {
    actorRefForRunId.get(runId) match {
      case Some(actorRef) => actorRef ! ConfigAbort(runId)
      case None => log.error(s"Can't abort config: No worker actor found for runId: $runId")
    }
  }


  /*
        config {
        info {
          configId = 1000233
          obsId = TMT-2021A-C-2-1
        }
        tmt.tel.base.pos {
          posName = NGC738B
          c1 = "22:35:58.530"
          c2 = "33:57:55.40"
          equinox = J2000
        }
        tmt.tel.ao.pos.one {
          c1 = "22:356:01.066"
          c2 = "33:58:21.69"
          equinox = J2000
        }
      }

   */
  override def query(config: Configuration, replyTo: ActorRef): Unit = {
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
            withValue("c1", "22:356:01.066").
            withValue("c2", "33:58:21.69").
            withValue("equinox", "J2000")
        }
    }

    sender ! ConfigResponse(Success(conf))
    savedConfig = Some(config)
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

