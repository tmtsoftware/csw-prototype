package org.tmt.csw.cmd

import akka.actor._
import com.typesafe.config.Config
import akka.pattern.ask
import scala.concurrent.Future
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.cmd.CommandServiceActor.QueuePause
import org.tmt.csw.cmd.CommandServiceActor.QueueStop
import org.tmt.csw.cmd.CommandServiceActor.QueueDelete
import org.tmt.csw.cmd.CommandServiceActor.QueueStart
import org.tmt.csw.cmd.CommandServiceActor.QueueSubmit
import org.tmt.csw.cmd.CommandServiceActor.QueueRequest

/**
 * Contains actor messages received
 */
object CommandServiceActor {
  // TMT Standard Queue Interaction Commands
  sealed trait QueueInteractionCommand
  case class QueueSubmit(configs: Config*) extends QueueInteractionCommand
  case class QueueRequest(configs: Config*) extends QueueInteractionCommand
  case class QueueStop() extends QueueInteractionCommand
  case class QueuePause() extends QueueInteractionCommand
  case class QueueStart() extends QueueInteractionCommand
  case class QueueDelete(runId: RunId) extends QueueInteractionCommand
}

/**
 * Implements the TMT Command Service
 */
class CommandServiceActor(component: OmoaComponent) extends Actor {

  val queueActor = context.actorOf(Props(new QueueActor(component)), name = "queueActorFor" + component.getName)


  def receive = {
    case QueueSubmit(configs) => sender ! queueSubmit(configs)
    case QueueRequest(configs) => sender ! queueRequest(configs)
    case QueueStop() => queueStop()
    case QueuePause() => queuePause()
    case QueueStart() => queueStart()
    case QueueDelete(runId) => queueDelete(runId)

    // TODO add other message types here ...

    // Status Messages (XXX TODO: send events for these? Or send them directly to the original sender?)
    case CommandStatus.StatusQueued =>
    case CommandStatus.StatusBusy =>
    case CommandStatus.StatusComplete =>
    case CommandStatus.StatusError =>
    case CommandStatus.StatusAborted =>

    case _ => sender ! Status.Failure(new IllegalArgumentException)
  }

  /**
   * Submit one or more configs to the component's command queue and return the run id.
   */
  private def queueSubmit(configs: Config*): RunId = {
    val runId = RunId()
    queueActor ! QueueActor.QueueSubmit(QueueConfig(runId, configs))
    // XXX TODO: send status pending event?
    runId
  }

  /**
   * Request immediate execution of one or more configs on the component and return a future with the status
   * (which should be
   */
  private def queueRequest(configs: Config*): Future[CommandStatus] = {
    implicit val timeout = Timeout(5.seconds)
    (queueActor ? QueueActor.QueueRequest(QueueConfig(RunId(), configs))).mapTo[CommandStatus]
  }

  /**
   * Processing of Configurations in a components queue is stopped. All Configurations currently in the
   * queue are removed. No components are accepted or processed while stopped.
   */
  private def queueStop() {
    queueActor ! QueueActor.QueueStop()
    queueActor ! Kill
  }

  /**
   * Pause the processing of a component’s queue after the completion of the current Configuration.
   * No changes are made to the queue.
   */
  private def queuePause() {
    queueActor ! QueueActor.QueuePause()
  }

  /**
   * Processing of component’s queue is started.
   */
  private def queueStart() {
    queueActor ! QueueActor.QueueStart()
  }

  /**
   * Allows removal of a Configuration in the queued execution state.
   */
  private def queueDelete(runId: RunId) {
    queueActor ! QueueActor.QueueDelete(runId)
  }
}