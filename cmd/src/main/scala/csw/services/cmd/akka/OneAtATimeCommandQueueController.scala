package csw.services.cmd.akka

import csw.services.cmd.akka.CommandServiceActor._

/**
 * A CommandServiceActor that extends this trait feeds new configs to the config actor only after
 * it has completed (canceled or aborted) the previous config.
 */
trait OneAtATimeCommandQueueController {
  this: CommandServiceActor ⇒

  // Create the queue controller actor
  override val commandQueueControllerActor = context.actorOf(OneAtATimeCommandQueueControllerActor.props(
    commandQueueActor, commandStatusActor), name = commandQueueControllerActorName)

  // Display name for this queue controller
  override val commandQueueControllerType: String = "one at a time"

}
