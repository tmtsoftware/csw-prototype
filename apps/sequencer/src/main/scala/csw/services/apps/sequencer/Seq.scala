package csw.services.apps.sequencer

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.services.ccs.HcdController.Submit
import csw.services.ccs.{AssemblyClient, BlockingAssemblyClient}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.{Location, ResolvedAkkaLocation}
import csw.services.loc.{ComponentId, ComponentType, Connection, LocationService}
import csw.services.pkg.{ContainerComponent, Supervisor}
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Defines utility method and shortcuts for use in the sequencer shell
 */
object Seq {
  LocationService.initInterface()
  implicit val system = ActorSystem("Sequencer")
  implicit val timeout: Timeout = 60.seconds

  private def getActorRef(locations: Set[Location], connection: Connection): ActorRef = {
    locations.collect {
      case ResolvedAkkaLocation(conn, uri, prefix, actorRefOpt) if connection == conn => actorRefOpt.get
    }.head
  }

  /**
   * Returns a client object to use to access the given assembly
   *
   * @param name the name of the assembly
   * @return the client object
   */
  def resolveAssembly(name: String): BlockingAssemblyClient = {
    val connection = AkkaConnection(ComponentId(name, ComponentType.Assembly))
    val info = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    val actorRef = getActorRef(info.locations, connection)
    BlockingAssemblyClient(AssemblyClient(actorRef))
  }

  /**
   * Returns a client object to use to access the given HCD
   *
   * @param name the name of the HCD
   * @return the client object
   */
  def resolveHcd(name: String): HcdClient = {
    val connection = AkkaConnection(ComponentId(name, ComponentType.HCD))
    val info = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    val actorRef = getActorRef(info.locations, connection)
    HcdClient(actorRef)
  }

  /**
   * Returns a client object to use to access the given container
   *
   * @param name the name of the container
   * @return the client object
   */
  def resolveContainer(name: String): ContainerClient = {
    val connection = AkkaConnection(ComponentId(name, ComponentType.Container))
    val info = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    val actorRef = getActorRef(info.locations, connection)
    ContainerClient(actorRef)
  }

  /**
   * Returns a client object for working with the given container actor
   *
   * @param actorRef the container actor
   */
  case class ContainerClient(actorRef: ActorRef) {
    def stop(): Unit = actorRef ! ContainerComponent.Stop
    def halt(): Unit = actorRef ! ContainerComponent.Halt
    def restart(): Unit = actorRef ! ContainerComponent.Restart
    // TODO: add more...
  }

  /**
   * Returns a client object for working with the given HCD actor
   *
   * @param actorRef the HCD actor
   */
  case class HcdClient(actorRef: ActorRef) {
    def submit(config: SetupConfig): Unit = {
      actorRef ! Submit(config)
    }
  }

}
