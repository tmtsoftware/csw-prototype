package csw.services.cmd.akka

import akka.actor.{ ActorContext, ActorRef, Props }
import akka.event.LoggingAdapter
import csw.shared.RunId

/**
 * A utility class for managing worker actors where there is one worker per run id.
 * @param name the base name to use for the actors (-runId will be appended)
 * @param context the actor context
 * @param log the actor's logger
 */
case class WorkerPerRunId(name: String, context: ActorContext, log: LoggingAdapter) {
  /**
   * creates a new submit worker actor to handle the submit for the given runId
   * @param props used to create the actor
   * @param runId the runId to associate with this actor
   * @return the new actor ref, or if an actor already exists for this runId, None
   */
  def newWorkerFor(props: Props, runId: RunId): Option[ActorRef] = {
    val name = actorName(runId)
    context.child(name) match {
      case Some(actorRef) ⇒
        log.error(s"$actorRef already exists")
        None
      case None ⇒
        Some(context.actorOf(props, actorName(runId)))
    }

  }

  /**
   * Gets an existing CommandServiceWorker actor for the given runId
   * @param runId the runId that the actor was created for
   * @return Some(actorRef) if found, otherwise None
   */
  def getWorkerFor(runId: RunId): Option[ActorRef] = {
    context.child(actorName(runId))
  }

  // Gets the name of the actor for the given runId
  private def actorName(runId: RunId): String = {
    s"$name-${runId.id}"
  }
}
