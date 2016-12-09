package csw.examples.vslice.seq

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.util.config.Configurations
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.services.ccs.HcdController.Submit
import csw.services.ccs.{AssemblyControllerClient, BlockingAssemblyClient}
import csw.services.loc.{ComponentId, ComponentType, Connection, LocationService}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.{Location, ResolvedAkkaLocation}
import csw.services.pkg.ContainerComponent

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Defines utility method and shortcuts for use in the sequencer shell
  */
object SeqSupport {
  //  LocationService.initInterface()
  implicit val system = ActorSystem("Sequencer")
  implicit val timeout: Timeout = 5.seconds

  private def getActorRef(locations: Set[Location], connection: Connection): ActorRef = {
    locations.collect {
      case ResolvedAkkaLocation(conn, _, _, actorRefOpt) if connection == conn => actorRefOpt.get
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
    BlockingAssemblyClient(AssemblyControllerClient(actorRef))
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

/**
  * TMT Source Code: 12/4/16.
  */
object Demo {
  import SeqSupport._

  implicit val timeout = Timeout(10.seconds)

  val taName = "lgsTrombone"
  val thName = "lgsTromboneHCD"

  val componentPrefix: String = "nfiraos.ncc.trombone"

  // Public command configurations
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val initCK: ConfigKey = initPrefix

  // Dataum submit commandxit
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK: ConfigKey = datumPrefix

  val sca1 = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

  def getTrombone():BlockingAssemblyClient = resolveAssembly(taName)

  // Need to add subscribing to a SystemEvent and subscribing to StatusEvents so a command to look up each of the services
  // is needed and something to subscribe maybe.




}
