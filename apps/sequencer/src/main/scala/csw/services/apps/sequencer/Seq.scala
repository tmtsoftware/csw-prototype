package csw.services.apps.sequencer

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import csw.services.ccs.HcdController.Submit
import csw.services.ccs.{ AssemblyClient, BlockingAssemblyClient }
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.{ ServiceRef, ServiceId, LocationService, ServiceType }
import csw.services.pkg.{ Supervisor, Container }
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Defines utility method and shortcuts for use in the sequencer shell
 */
object Seq {
  implicit val system = ActorSystem("Sequencer")
  implicit val timeout: Timeout = 60.seconds

  private def resolve(name: String, serviceType: ServiceType): BlockingAssemblyClient = {
    val serviceRef = ServiceRef(ServiceId(name, serviceType), AkkaType)
    val info = Await.result(LocationService.resolve(Set(serviceRef)), timeout.duration)
    val actorRef = info.services(serviceRef).actorRefOpt.get
    BlockingAssemblyClient(AssemblyClient(actorRef))
  }

  /**
   * Returns a client object to use to access the given HCD
   * @param name the name of the HCD
   * @return the client object
   */
  def resolveHcd(name: String): HcdClient = {
    val serviceRef = ServiceRef(ServiceId(name, ServiceType.HCD), AkkaType)
    val info = Await.result(LocationService.resolve(Set(serviceRef)), timeout.duration)
    val actorRef = info.services(serviceRef).actorRefOpt.get
    HcdClient(actorRef)
  }

  /**
   * Returns a client object to use to access the given assembly
   * @param name the name of the assembly
   * @return the client object
   */
  def resolveAssembly(name: String): BlockingAssemblyClient = resolve(name, ServiceType.Assembly)

  /**
   * Returns a client object to use to access the given container
   * @param name the name of the container
   * @return the client object
   */
  def resolveContainer(name: String): ContainerClient = {
    val serviceRef = ServiceRef(ServiceId(name, ServiceType.Container), AkkaType)
    val info = Await.result(LocationService.resolve(Set(serviceRef)), timeout.duration)
    ContainerClient(info.services(serviceRef).actorRefOpt.get)
  }

  /**
   * Returns a client object for working with the given container actor
   * @param actorRef the container actor
   */
  case class ContainerClient(actorRef: ActorRef) {
    def stop(): Unit = actorRef ! Container.Stop
    def halt(): Unit = actorRef ! Container.Halt
    def restart(): Unit = actorRef ! Container.Restart
    def initialize(): Unit = actorRef ! Supervisor.Initialize
    def Startup(): Unit = actorRef ! Supervisor.Startup
    def shutdown(): Unit = actorRef ! Supervisor.Shutdown
    def uninitialize(): Unit = actorRef ! Supervisor.Uninitialize
  }

  /**
   * Returns a client object for working with the given HCD actor
   * @param actorRef the HCD actor
   */
  case class HcdClient(actorRef: ActorRef) {
    def submit(config: SetupConfig): Unit = {
      actorRef ! Submit(config)
    }
  }

}
