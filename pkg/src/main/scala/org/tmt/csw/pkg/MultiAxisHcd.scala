package org.tmt.csw.pkg

import org.tmt.csw.cmd.akka.MultiAxisCommandServiceActor
import akka.actor.ActorRef

/**
 * Represents a multi-axis HCD (Hardware Control Daemon) component.
 */
trait MultiAxisHcd extends Component with MultiAxisCommandServiceActor  {

  // Receive actor messages.
  def receiveHcdMessages: Receive = receiveComponentMessages orElse receiveCommands

  override def terminated(actorRef: ActorRef): Unit = log.info(s"Actor $actorRef terminated")
}
