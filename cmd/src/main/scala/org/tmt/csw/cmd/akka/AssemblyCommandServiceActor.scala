package org.tmt.csw.cmd.akka

import akka.actor.ActorRef
import org.tmt.csw.cmd.akka.CommandQueueActor.ConfigQueueStatus
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.ls.LocationServiceActor.ServiceId
import org.tmt.csw.ls.LocationService


/**
 * A command service actor that delegates to other HCD command service actors.
 */
trait AssemblyCommandServiceActor extends CommandServiceActor {
  import CommandServiceActor._

  // Add a ConfigDistributorActor to distribute the incoming configs to the HCDs
  val configDistributorActor = context.actorOf(ConfigDistributorActor.props(commandStatusActor), name = configDistributorActorName)
  override val configActor = configDistributorActor


  // Handle command service commands plus status requests.
  override def receiveCommands: Receive = super.receiveCommands orElse {
    case StatusRequest => handleStatusRequest(sender)
  }

  /**
   * Answers a request for status.
   * @param requester the actor requesting the status
   */
  def handleStatusRequest(requester: ActorRef): Unit = {
    implicit val timeout = Timeout(5.seconds)
    implicit val dispatcher = context.system.dispatcher
    for {
      queueStatus  <- (commandQueueActor ? StatusRequest).mapTo[ConfigQueueStatus]
    } {
      requester ! CommandServiceStatus(self.path.name, queueStatus, commandQueueControllerType)
    }
  }
}
