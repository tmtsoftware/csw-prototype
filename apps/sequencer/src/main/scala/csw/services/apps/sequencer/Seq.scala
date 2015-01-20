package csw.services.apps.sequencer

import akka.actor.ActorSystem
import csw.services.cmd.akka.{ BlockingCommandServiceClient, CommandServiceClient, CommandServiceClientActor }
import csw.services.ls.LocationService
import csw.services.ls.LocationServiceActor.{ ServiceId, ServiceType }

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Defines utility method and shortcuts for use in the sequencer shell
 */
object Seq {
  implicit val system = ActorSystem("Sequencer")
  val duration = 5.seconds

  private def resolve(name: String, serviceType: ServiceType): BlockingCommandServiceClient = {
    val info = Await.result(LocationService.resolve(ServiceId(name, serviceType)), duration)
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
