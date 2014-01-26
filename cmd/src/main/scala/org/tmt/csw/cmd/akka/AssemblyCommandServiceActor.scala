package org.tmt.csw.cmd.akka

import akka.actor.{ActorRef, Props}
import org.tmt.csw.cmd.akka.CommandQueueActor.ConfigQueueStatus
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._


/**
 * A command service actor that delegates to other HCD command service actors.
 */
trait AssemblyCommandServiceActor extends CommandServiceActor {
  import CommandServiceActor._
  import ConfigRegistrationActor._

  // Add a ConfigDistributorActor to distribute the configs to the HCDs
  val configDistributorActor = context.actorOf(ConfigDistributorActor.props(commandStatusActor), name = configDistributorActorName)
  override val configActor = configDistributorActor
  override val configPaths = Set.empty[String]

  // Registry of actors that receive the configurations
  val configRegistrationActor = context.actorOf(Props[ConfigRegistrationActor], name = configRegistrationActorName)

  // the ConfigDistributorActor will receive information about the registered HCDs
  configRegistrationActor ! Subscribe(configDistributorActor)

  override def receiveCommands: Receive = super.receiveCommands orElse {
    // Forward any registration requests from slave command service actors to the registration actor
    case r@Register(actorRef, paths) => configRegistrationActor forward r
    case d@Deregister(actorRef) => configRegistrationActor forward d

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
      registryStatus <- (configRegistrationActor ? StatusRequest).mapTo[RegistryStatus]
    } {
      requester ! CommandServiceStatus(self.path.name, queueStatus, commandQueueControllerType, Some(registryStatus))
    }
  }
}
