package org.tmt.csw.cmd.akka

import akka.actor._
import scala.concurrent.duration._
import org.tmt.csw.cmd.core.Configuration
import scala.util.Success
import org.tmt.csw.cmd.akka.CommandQueueActor.SubmitWithRunId

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
    case ConfigGet(config) => query(config, sender)
    case ConfigPut(config) =>
    case x => unknownMessage(x)
  }

  // State while paused
  def paused: Receive = {
    case ConfigResume(runId) => resume(runId)
    case ConfigCancel(runId) => cancel(None, runId)
    case ConfigAbort(runId) => abort(None, runId)
    case ConfigPause(runId) => pause(None, runId)
    case ConfigGet(config) => query(config, sender)
    case ConfigPut(config) =>
    case x => unknownMessage(x)
  }

  def newTimer(): Cancellable = {
    implicit val dispatcher = context.system.dispatcher
    context.system.scheduler.scheduleOnce(numberOfSecondsToRun seconds, self, WorkDone)
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
//    commandStatusActor ! CommandStatusActor.StatusUpdate(CommandStatus.Paused(runId), submit.submitter)
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
