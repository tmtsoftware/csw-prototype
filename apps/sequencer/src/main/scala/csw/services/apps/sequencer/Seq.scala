package csw.services.apps.sequencer

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.services.ccs.{AssemblyClient, BlockingAssemblyClient}
import csw.services.loc.LocationService
import csw.util.Components._
import csw.services.pkg.{ContainerComponent$, Supervisor$}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Defines utility method and shortcuts for use in the sequencer shell
 */
object Seq {
  implicit val system = ActorSystem("Sequencer")
  implicit val timeout: Timeout = 60.seconds
/*
  private def resolve(name: String, componentType: ComponentType): BlockingAssemblyClient = {
    val connection = Connection(ComponentId(name, componentType), AkkaType)
    val info = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    val actorRef = info.services(connection).actorRefOpt.get
    BlockingAssemblyClient(AssemblyClient(actorRef))
  }

  /**
   * Returns a client object to use to access the given assembly
   *
   * @param name the name of the assembly
   * @return the client object
   */
  def resolveAssembly(name: String): BlockingAssemblyClient = resolve(name, Assembly)

  /**
   * Returns a client object to use to access the given container
   *
   * @param name the name of the container
   * @return the client object
   */
  def resolveContainer(name: String): ContainerClient = {
    val connection = Connection(ComponentId(name, Container), AkkaType)
    val info = Await.result(LocationService.resolve(Set(connection)), timeout.duration)
    ContainerClient(info.services(connection).actorRefOpt.get)
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
    // TODO
    //def initialize(): Unit = actorRef ! Supervisor.Initialize
    //def Startup(): Unit = actorRef ! Supervisor.Startup
    //def shutdown(): Unit = actorRef ! Supervisor.Shutdown
    //def uninitialize(): Unit = actorRef ! Supervisor.Uninitialize
  }
  */
}
