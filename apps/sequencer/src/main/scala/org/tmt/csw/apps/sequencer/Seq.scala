package org.tmt.csw.apps.sequencer

import akka.actor.ActorSystem
import org.tmt.csw.ls.LocationServiceActor.{ServiceId, ServiceType}
import org.tmt.csw.cmd.akka.{BlockingCommandServiceClient, CommandStatus, CommandServiceClientActor, CommandServiceClient}
import scala.concurrent.Await
import org.tmt.csw.ls.LocationService
import org.tmt.csw.util.Configuration
import akka.pattern.ask
import scala.concurrent.duration._

/**
 * Defines utility method and shortcuts for use in the sequencer shell
 */
object Seq {
  val system = ActorSystem("Sequencer")
  val duration = 5.seconds

  private def resolve(name: String, serviceType: ServiceType): BlockingCommandServiceClient = {
    val info = Await.result(LocationService.resolve(system, ServiceId(name, serviceType)), duration)
    val actorRef = info.actorRefOpt.get
    val clientActor = system.actorOf(CommandServiceClientActor.props(actorRef, duration))
    BlockingCommandServiceClient(CommandServiceClient(clientActor, duration))
  }

  /**
   * Returns a client object to use to access the given HCD
   * @param name the name of the HCD
   * @return the client object
   */
  def resolveHcd(name: String): BlockingCommandServiceClient = resolve(name, ServiceType.HCD)

  /**
   * Returns a client object to use to access the given assembly
   * @param name the name of the assembly
   * @return the client object
   */
  def resolveAssembly(name: String): BlockingCommandServiceClient = resolve(name, ServiceType.Assembly)

}
