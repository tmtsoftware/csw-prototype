package org.tmt.csw.cmd.akka

import akka.actor.{Props, ActorContext, ActorRef}

/**
 * A utility class for managing worker actors where there is one worker per run id.
 * @param name the base name to use for the actors (-$runId will be appended)
 */
case class WorkerPerRunId(name: String, context: ActorContext) {
  /**
   * creates a new submit worker actor to handle the submit for the given runId
   * @param props used to create the actor
   * @param runId the runId to associate with this actor
   * @return the new actor ref
   */
  def newWorkerFor(props: Props, runId: RunId): ActorRef = {
    context.actorOf(props, actorName(runId))
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
