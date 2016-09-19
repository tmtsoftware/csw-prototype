package csw.services.ccs

import akka.actor.ActorRef
import HcdController._
import csw.util.akka.PublisherActorClient
import csw.util.config.Configurations.SetupConfig

/**
 * A client API for the HcdController actor.
 *
 * Note that Subscribers to the HCD's state will receive CurrentState messages
 * whenever the HCD's state changes.
 *
 * @param hcdActorRef the HcdController actor ref
 */
class HcdControllerClient(hcdActorRef: ActorRef) extends PublisherActorClient(hcdActorRef) {
  /**
   * Submits a configuration to the HCD
   */
  def submit(config: SetupConfig): Unit = hcdActorRef ! Submit(config)

}
