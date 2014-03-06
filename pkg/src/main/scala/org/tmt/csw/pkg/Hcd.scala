package org.tmt.csw.pkg

import org.tmt.csw.cmd.akka.{ConfigActor, CommandServiceActor}
import akka.actor.ActorRef
import org.tmt.csw.ls.LocationServiceActor.ServiceId
import org.tmt.csw.ls.LocationServiceActor.ServiceType
import java.net.URI
import org.tmt.csw.ls.LocationService

/**
 * Represents an HCD (Hardware Control Daemon) component.
 */
trait Hcd extends Component with CommandServiceActor  {

  // Receive actor messages.
  def receiveHcdMessages: Receive = receiveComponentMessages orElse receiveCommands

  override def terminated(actorRef: ActorRef): Unit = log.info(s"Actor $actorRef terminated")

  /**
   * Register with the location service (which must be started as a separate process).
   * @param configPathOpt an optional path in a config message that this HCD is interested in
   * @param httpUri an optional HTTP/REST URI for the actor (if it uses Spray, for example)
   */
  def registerWithLocationService(configPathOpt: Option[String] = None,
                                  httpUri: Option[URI] = None) {
    val serviceId = ServiceId(name, ServiceType.HCD)
    log.info(s"Registering $serviceId ($configPathOpt) with the location service")
    LocationService.register(context.system, self, serviceId, configPathOpt)
  }
}
