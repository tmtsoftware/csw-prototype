package org.tmt.csw.cmd.akka

import org.tmt.csw.cmd.akka.CommandServiceActor._

/**
 * A MultiAxisCommandServiceActor that extends this trait feeds new configs to the individual config actors only after
 * they have completed (canceled or aborted) the previous config.
 */
class MultiAxisOneAtATimeCommandQueueController {
  this: MultiAxisCommandServiceActor =>

  override def initCommandQueueControllerActors(): Unit = {
    for (i <- 0 to axisCount) {
      commandQueueControllerActors(i) = context.actorOf(OneAtATimeCommandQueueControllerActor.props(
        commandQueueActors(i), commandStatusActors(i)), name = commandQueueControllerActorName + i)
    }
  }
}
