package csw.services.cmd.akka

import akka.actor._
import csw.services.cmd.akka.ConfigActor._
import csw.services.cmd.akka.CommandQueueActor._
import scala.util.Success
import scala.concurrent.duration._
import csw.util.Configuration

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

  // Used to create and get the worker actor that is handling a config
  val configWorkers = WorkerPerRunId("configWorkerActor", context, log)

  // XXX dummy config for test of get/query
  private var savedConfig: Option[Configuration] = None


  // Receive config messages
  override def receive: Receive = receiveConfigs

  /**
   * Called when a configuration is submitted
   */
  override def submit(submit: SubmitWithRunId): Unit = {
    val props = TestConfigActorWorker.props(submit, commandStatusActor, numberOfSecondsToRun)
    configWorkers.newWorkerFor(props, submit.runId) match {
      case Some(configWorkerActor) =>
        log.debug(s"Forwarding config ${submit.config} to worker $configWorkerActor")
        context.watch(configWorkerActor)
      case None =>
    }
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  override def pause(runId: RunId): Unit = {
    configWorkers.getWorkerFor(runId) match {
      case Some(actorRef) => actorRef ! ConfigPause(runId)
      case None => log.error(s"Can't pause config: No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  override def resume(runId: RunId): Unit = {
    configWorkers.getWorkerFor(runId) match {
      case Some(actorRef) => actorRef ! ConfigResume(runId)
      case None => log.error(s"Can't resume config: No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  override def cancel(runId: RunId): Unit = {
    configWorkers.getWorkerFor(runId) match {
      case Some(actorRef) => actorRef ! ConfigCancel(runId)
      case None => log.error(s"Can't cancel config: No worker actor found for runId: $runId")
    }
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  override def abort(runId: RunId): Unit = {
    configWorkers.getWorkerFor(runId) match {
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

    sender() ! ConfigResponse(Success(conf))
    savedConfig = Some(config)
  }
}


// ----


object TestConfigActorWorker {
  /**
   * Props to create a test config worker actor.
   *
   * @param submit: The submit message that this actor should work on
   * @param commandStatusActor actor that receives the command status messages
   * @param numberOfSecondsToRun the number of seconds to run the simulated work
   */
  def props(submit: SubmitWithRunId, commandStatusActor: ActorRef, numberOfSecondsToRun: Int): Props =
    Props(classOf[TestConfigActorWorker], submit, commandStatusActor, numberOfSecondsToRun)

  // Object passed in timeout messages
  private case object WorkDone

}

class TestConfigActorWorker(submit: SubmitWithRunId, val commandStatusActor: ActorRef, numberOfSecondsToRun: Int)
  extends Actor with ActorLogging {

  import TestConfigActorWorker._
  import ConfigActor._

  // Set a timer to simulate the time it takes to do something
  context.become(busy(newTimer()))

  override def receive: Receive = {
    case x => unknownMessage(x)
  }

  // State while busy working
  def busy(timer: Cancellable): Receive = {
    case WorkDone => workDone(timer)
    case ConfigCancel(runId) => cancel(Some(timer), runId)
    case ConfigAbort(runId) => abort(Some(timer), runId)
    case ConfigPause(runId) => pause(Some(timer), runId)
    case ConfigGet(config) => query(config, sender())
    case ConfigPut(config) =>
    case x => unknownMessage(x)
  }

  // State while paused
  def paused: Receive = {
    case ConfigResume(runId) => resume(runId)
    case ConfigCancel(runId) => cancel(None, runId)
    case ConfigAbort(runId) => abort(None, runId)
    case ConfigPause(runId) => pause(None, runId)
    case ConfigGet(config) => query(config, sender())
    case ConfigPut(config) =>
    case x => unknownMessage(x)
  }

  def newTimer(): Cancellable = {
    implicit val dispatcher = context.system.dispatcher
    context.system.scheduler.scheduleOnce(numberOfSecondsToRun.seconds, self, WorkDone)
  }

  def unknownMessage(x: Any): Unit = {
    log.error(s"Received unknown message: $x")
  }

  // Called when the work is done
  def workDone(timer: Cancellable): Unit = {
    timer.cancel()
    commandStatusActor ! CommandStatusActor.StatusUpdate(CommandStatus.Completed(submit.runId), submit.submitter)
    self ! PoisonPill
  }

  /**
   * Work on the config matching the given runId should be paused
   */
  def pause(timer: Option[Cancellable], runId: RunId): Unit = {
    if (timer.isDefined) timer.get.cancel()
    context.become(paused)
  }

  /**
   * Work on the config matching the given runId should be resumed
   */
  def resume(runId: RunId): Unit = {
    context.become(busy(newTimer()))
  }

  /**
   * Work on the config matching the given runId should be canceled
   */
  def cancel(timer: Option[Cancellable], runId: RunId): Unit = {
    if (timer.isDefined) timer.get.cancel()
    commandStatusActor ! CommandStatusActor.StatusUpdate(CommandStatus.Canceled(runId), submit.submitter)
    self ! PoisonPill
  }

  /**
   * Work on the config matching the given runId should be aborted
   */
  def abort(timer: Option[Cancellable], runId: RunId): Unit = {
    if (timer.isDefined) timer.get.cancel()
    commandStatusActor ! CommandStatusActor.StatusUpdate(CommandStatus.Aborted(runId), submit.submitter)
    self ! PoisonPill
  }

  def query(config: Configuration, replyTo: ActorRef): Unit = {
    replyTo ! ConfigResponse(Success(config)) // XXX dummy implementation
  }
}
