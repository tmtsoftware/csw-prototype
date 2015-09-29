package csw.services.apps.sequencer

import akka.actor.{ ActorRef, ActorSystem }
import csw.services.cmd_old.akka.{ BlockingCommandServiceClient, CommandServiceClient, CommandServiceClientActor }
import csw.services.ls.LocationService
import csw.services.ls.LocationServiceActor.{ ServiceId, ServiceType }
import csw.services.pkg_old.{ LifecycleManager, Container }

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

  /**
   * Returns a client object to use to access the given container
   * @param name the name of the container
   * @return the client object
   */
  def resolveContainer(name: String): ContainerClient = {
    val info = Await.result(LocationService.resolve(ServiceId(name, ServiceType.Container)), duration)
    ContainerClient(info.actorRefOpt.get)
  }

  /**
   * Returns a client object for working with the given container actor
   * @param actorRef the container actor
   */
  case class ContainerClient(actorRef: ActorRef) {
    def stop(): Unit = actorRef ! Container.Stop
    def halt(): Unit = actorRef ! Container.Halt
    def restart(): Unit = actorRef ! Container.Restart
    def initialize(): Unit = actorRef ! LifecycleManager.Initialize
    def Startup(): Unit = actorRef ! LifecycleManager.Startup
    def shutdown(): Unit = actorRef ! LifecycleManager.Shutdown
    def uninitialize(): Unit = actorRef ! LifecycleManager.Uninitialize
  }
}
