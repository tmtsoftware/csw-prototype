package org.tmt.csw.pkg

import akka.actor.{ActorPath, ActorRef}
import org.tmt.csw.cmd.akka.AssemblyCommandServiceActor
import akka.util.Timeout
import scala.concurrent.duration._
import org.tmt.csw.ls.LocationService
import org.tmt.csw.ls.LocationServiceActor.ServiceId


//object Assembly {
//  // Assembly actor messages
//  sealed trait AssemblyMessage
//  // Adds a component actor to the assembly
//  case class AddComponent(actorRef: ActorRef) extends AssemblyMessage
//  case class AddComponentByPath(actorPath: ActorPath) extends AssemblyMessage
//  // Removes a component actor from the assembly
//  case class RemoveComponent(actorRef: ActorRef) extends AssemblyMessage
//  case class RemoveComponentByPath(actorPath: ActorPath) extends AssemblyMessage
//}

/**
 * Assemblies represent user-oriented devices and can be assembled from multiple HCDs
 */
trait Assembly extends Component with AssemblyCommandServiceActor {

  val duration: FiniteDuration = 5.seconds
  implicit val timeout = Timeout(duration)

  /**
   * Request information about the services (HCDs, other assemblies) that will be used by this assembly.
   * @param serviceIds a list of the names and types of the HCDs that will be used
   */
  def requestServices(serviceIds: List[ServiceId]): Unit = {
    log.info(s"Request services: $serviceIds")
    LocationService.requestServices(context.system, configDistributorActor, serviceIds)
  }

  // Receive actor messages
  def receiveAssemblyMessages: Receive = receiveComponentMessages orElse receiveCommands

  override def terminated(actorRef: ActorRef): Unit = log.info(s"Actor $actorRef terminated")
}
