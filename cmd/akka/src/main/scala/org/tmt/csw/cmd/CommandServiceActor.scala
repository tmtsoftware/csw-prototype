package org.tmt.csw.cmd

import akka.actor.{ActorRef, Props, Status, Actor}
import com.typesafe.config.Config
import scala.collection.mutable
import akka.pattern.ask
import scala.concurrent.Future
import CommandServiceActor._
import akka.util.Timeout
import scala.concurrent.duration._

// Contains actor messages received
object CommandServiceActor {
  // TMT Standard Queue Interaction Commands
  sealed trait QueueInteractionCommand
  case class QueueSubmit(component: OmoaComponent, configs: Config*) extends QueueInteractionCommand
  case class QueueRequest(component: OmoaComponent, configs: Config*) extends QueueInteractionCommand
  case class QueueStop(component: OmoaComponent) extends QueueInteractionCommand
  case class QueuePause(component: OmoaComponent) extends QueueInteractionCommand
  case class QueueStart(component: OmoaComponent) extends QueueInteractionCommand
  case class QueueDelete(component: OmoaComponent, runId: RunId) extends QueueInteractionCommand
}

/**
 * Implements the TMT Command Service
 */
class CommandServiceActor() extends Actor {

  val actorMap = mutable.Map[OmoaComponent, ActorRef]()

  def receive = {
    case QueueSubmit(component, configs) => sender ! queueSubmit(component, configs)
    case QueueRequest(component, configs) => sender ! queueRequest(component, configs)
    case QueueStop(component) => queueStop(component)
    case QueuePause(component) => queuePause(component)
    case QueueStart(component) => queueStart(component)
    case QueueDelete(component, runId) => queueDelete(component, runId)

    // TODO add other message types here ...

    // Status Messages (XXX TODO: send events for these)
    case CommandStatus.StatusQueued =>
    case CommandStatus.StatusBusy =>
    case CommandStatus.StatusComplete =>
    case CommandStatus.StatusError =>
    case CommandStatus.StatusAborted =>

    case _ => sender ! Status.Failure(new IllegalArgumentException)
  }

  // Returns an actor for working on the given component's command queue,
  // creating one if it does not already exist.
  private def queueActorFor(component: OmoaComponent): ActorRef = {
    if (actorMap.contains(component)) {
      actorMap(component)
    } else {
      val actor = context.actorOf(Props(new QueueActor(component)))
      actorMap(component) = actor
      actor
    }
  }

  /**
   * Submit one or more configs to the component's command queue and return the run id.
   */
  private def queueSubmit(component: OmoaComponent, configs: Config*): RunId = {
    val runId = RunId()
    queueActorFor(component) ! QueueActor.QueueSubmit(CommandConfig(runId, configs))
    // XXX TODO: send status pending event?
    runId
  }

  /**
   * Request immediate execution of one or more configs on the component and return a future with the status
   * (which should be
   */
  private def queueRequest(component: OmoaComponent, configs: Config*): Future[CommandStatus] = {
    implicit val timeout = Timeout(5.seconds)
    (queueActorFor(component) ? QueueActor.QueueRequest(CommandConfig(RunId(), configs))).mapTo[CommandStatus]
  }

  /**
   * Processing of Configurations in a components queue is stopped. All Configurations currently in the
   * queue are removed. No components are accepted or processed while stopped.
   */
  private def queueStop(component: OmoaComponent) {
    queueActorFor(component) ! QueueActor.QueueStop()
  }

  /**
   * Pause the processing of a component’s queue after the completion of the current Configuration.
   * No changes are made to the queue.
   */
  private def queuePause(component: OmoaComponent) {
    queueActorFor(component) ! QueueActor.QueuePause()
  }

  /**
   * Processing of component’s queue is started.
   */
  private def queueStart(component: OmoaComponent) {
    queueActorFor(component) ! QueueActor.QueueStart()
  }

  /**
   * Allows removal of a Configuration in the queued execution state.
   */
  private def queueDelete(component: OmoaComponent, runId: RunId) {
    queueActorFor(component) ! QueueActor.QueueDelete(runId)
  }
}
