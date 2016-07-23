package csw.services.cs.akka

import java.io.File

import akka.actor._
import akka.util.Timeout
import csw.services.cs.core.{ConfigFileHistory, _}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.loc.{ComponentId, ComponentType, LocationService}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Config service actor.
 *
 * Note: Only one instance of this actor should exist for a given local repository.
 */
object ConfigServiceActor {

  /**
   * Base trait of messages received by this actor
   */
  sealed trait ConfigServiceRequest

  /**
   * Message to create a new file in the repository. Reply is a [[CreateOrUpdateResult]] message.
   * @param path relative path to file in repo
   * @param configData the file's data
   * @param oversize true if the file should be stored in the config service annex for large files
   * @param comment optional comment
   */
  case class CreateRequest(path: File, configData: ConfigData, oversize: Boolean, comment: String = "") extends ConfigServiceRequest

  /**
   * Message to update the file. Reply is a [[CreateOrUpdateResult]] message.
   * @param path relative path to file in repo
   * @param configData the file's data
   * @param comment optional comment
   */
  case class UpdateRequest(path: File, configData: ConfigData, comment: String = "") extends ConfigServiceRequest

  /**
   * Message to create the file if it does not exist, otherwise update it. Reply is a [[CreateOrUpdateResult]] message.
   * @param path relative path to file in repo
   * @param configData the file's data
   * @param oversize true if the file should be stored in the config service annex for large files
   * @param comment optional comment
   */
  case class CreateOrUpdateRequest(path: File, configData: ConfigData, oversize: Boolean, comment: String = "") extends ConfigServiceRequest

  /**
   * Message to get the contents of a file from the repository. Reply is a [[GetResult]] message.
   * @param path relative path to file in repo
   * @param id optional id to get a specific version of the file
   */
  case class GetRequest(path: File, id: Option[ConfigId] = None) extends ConfigServiceRequest

  /**
   * Message to check if the file exists in the repo. Reply is a [[ExistsResult]] message.
   * @param path relative path to file in repo
   */
  case class ExistsRequest(path: File) extends ConfigServiceRequest

  /**
   * Message to delete the file in the repo. Reply is a [[UnitResult]] message.
   * @param path relative path to file in repo
   * @param comment optional comment
   */
  case class DeleteRequest(path: File, comment: String = "deleted") extends ConfigServiceRequest

  /**
   * Message to list all the files in the repo. Reply is a [[ListResult]] message.
   */
  case object ListRequest extends ConfigServiceRequest

  /**
   * Message to get the file history. Reply is a [[HistoryResult]] message.
   * @param path relative path to file in repo
   * @param maxResults optional max results
   */
  case class HistoryRequest(path: File, maxResults: Int = Int.MaxValue) extends ConfigServiceRequest

  /**
   * Sets the "default" file version. Reply is a [[UnitResult]] message.
   * @param path relative path to file in repo
   * @param id id of the default version
   */
  case class SetDefaultRequest(path: File, id: Option[ConfigId])

  /**
   * Resets the default to be the newest file version. Reply is a [[UnitResult]] message.
   * @param path relative path to file in repo
   */
  case class ResetDefaultRequest(path: File)

  /**
   * Gets the default version of the file. Reply is a [[UnitResult]] message.
   * @param path relative path to file in repo
   */
  case class GetDefaultRequest(path: File)

  /**
   * Reply messages (The final arguments, wrapped in Try[], give the actual results of a message request)
   */
  sealed trait ConfigServiceResult

  /**
   * Reply to a create or update message
   * @param path relative path to file in repo
   * @param configId the id of the file, if successful
   */
  case class CreateOrUpdateResult(path: File, configId: Try[ConfigId]) extends ConfigServiceResult

  /**
   * Reply to a [[GetRequest]]
   * @param path relative path to file in repo
   * @param id optional id of the file version
   * @param configData holds the file's data
   */
  case class GetResult(path: File, id: Option[ConfigId], configData: Try[Option[ConfigData]]) extends ConfigServiceResult

  /**
   * Reply to an [[ExistsRequest]] message
   * @param path relative path to file in repo
   * @param exists true if there were no errors and the path exists in the repo
   */
  case class ExistsResult(path: File, exists: Try[Boolean]) extends ConfigServiceResult

  /**
   * Reply to a [[ListRequest]]
   * @param list lists the files in the repo
   */
  case class ListResult(list: Try[List[ConfigFileInfo]]) extends ConfigServiceResult

  /**
   * Reply to a [[HistoryRequest]]
   * @param path relative path to file in repo
   * @param history lists the file's history
   */
  case class HistoryResult(path: File, history: Try[List[ConfigFileHistory]]) extends ConfigServiceResult

  /**
   * Reply message for operations that don't return a value ([[DeleteRequest]], [[SetDefaultRequest]], [[ResetDefaultRequest]])
   * @param path relative path to file in repo
   * @param status indicates if the operation was successful
   */
  case class UnitResult(path: File, status: Try[Unit]) extends ConfigServiceResult

  /**
   * Use this Props instance to initialize with the given ConfigManager
   *
   * @param configManager               used to access the repository
   * @param registerWithLocationService if true, register with the config service using configManager.name
   * @return the actor ref
   */
  def props(configManager: ConfigManager, registerWithLocationService: Boolean = false): Props =
    Props(classOf[ConfigServiceActor], configManager, registerWithLocationService)

  /**
   * Returns the default config manager, using the configured settings (see resources/reference.conf).
   *
   * @param system the caller's actor system, used to access the settings
   */
  def defaultConfigManager(implicit system: ActorSystem): ConfigManager = {
    val settings = ConfigServiceSettings(system)
    settings.getConfigManager
  }

  /**
   * Convenience method that gets the config service actor with the matching name
   * from the location service.
   *
   * @param name   the name of the config service (set in the config file, property csw.services.cs.name)
   * @param system the actor system
   * @return a future reference to the named config service actor
   */
  def locateConfigService(name: String = "")(implicit system: ActorSystem): Future[ActorRef] = {
    import system.dispatcher
    val csName = if (name.nonEmpty) name else ConfigServiceSettings.getConfigServiceSettings(system).name
    val componentId = ComponentId(csName, ComponentType.Service)
    val connection = AkkaConnection(componentId)
    implicit val timeout: Timeout = 60.seconds
    LocationService.resolve(Set(connection)).map(_.locations.head).mapTo[ResolvedAkkaLocation].map(_.actorRef.get)
  }
}

/**
 * An Akka actor class implementing the Config Service.
 *
 * @param configManager   the configManager to use to access the repo
 * @param registerWithLoc if true, register with the config service using configManager.name
 */
class ConfigServiceActor(configManager: ConfigManager, registerWithLoc: Boolean) extends Actor with ActorLogging {

  import context.dispatcher
  import csw.services.cs.akka.ConfigServiceActor._

  if (registerWithLoc) registerWithLocationService()

  // timeout for blocking wait (used to make sure local repo access is not concurrent)
  val timeout = 60.seconds

  log.debug("Started config service")

  // Registers with the location service
  def registerWithLocationService(): Unit = {
    val componentId = ComponentId(configManager.name, ComponentType.Service)
    LocationService.registerAkkaConnection(componentId, self)(context.system)
  }

  override def receive: Receive = {
    case CreateRequest(path, configData, oversize, comment) =>
      wrap(handleCreateRequest(sender(), path, configData, oversize, comment))
    case UpdateRequest(path, configData, comment) =>
      wrap(handleUpdateRequest(sender(), path, configData, comment))
    case CreateOrUpdateRequest(path, configData, oversize, comment) =>
      wrap(handleCreateOrUpdateRequest(sender(), path, configData, oversize, comment))
    case GetRequest(path, id) =>
      wrap(handleGetRequest(sender(), path, id))
    case ExistsRequest(path) =>
      wrap(handleExistsRequest(sender(), path))
    case DeleteRequest(path, comment) =>
      wrap(handleDeleteRequest(sender(), path, comment))
    case ListRequest =>
      wrap(handleListRequest(sender()))
    case HistoryRequest(path, maxResults) =>
      wrap(handleHistoryRequest(sender(), path, maxResults))
    case SetDefaultRequest(path, id) =>
      wrap(handleSetDefaultRequest(sender(), path, id))
    case ResetDefaultRequest(path) =>
      wrap(handleResetDefaultRequest(sender(), path))
    case GetDefaultRequest(path) =>
      wrap(handleGetDefaultRequest(sender(), path))

    case x => log.error(s"Received unknown message $x from ${sender()}")
  }

  // Used to wait for an operation to complete before receiving the next message
  def wrap(f: => Future[Unit]): Unit = {
    Await.ready(f, timeout)
  }

  def handleCreateRequest(replyTo: ActorRef, path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[Unit] = {
    val result = configManager.create(path, configData, oversize, comment)
    result onComplete {
      case Success(configId) => replyTo ! CreateOrUpdateResult(path, Success(configId))
      case Failure(ex)       => replyTo ! CreateOrUpdateResult(path, Failure(ex))
    }
    result.map(_ => ())
  }

  def handleUpdateRequest(replyTo: ActorRef, path: File, configData: ConfigData, comment: String): Future[Unit] = {
    val result = configManager.update(path, configData, comment)
    result onComplete {
      case Success(configId) => replyTo ! CreateOrUpdateResult(path, Success(configId))
      case Failure(ex)       => replyTo ! CreateOrUpdateResult(path, Failure(ex))
    }
    result.map(_ => ())
  }

  def handleCreateOrUpdateRequest(replyTo: ActorRef, path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[Unit] = {
    val result = configManager.createOrUpdate(path, configData, oversize, comment)
    result onComplete {
      case Success(configId) => replyTo ! CreateOrUpdateResult(path, Success(configId))
      case Failure(ex)       => replyTo ! CreateOrUpdateResult(path, Failure(ex))
    }
    result.map(_ => ())
  }

  def handleGetRequest(replyTo: ActorRef, path: File, id: Option[ConfigId]): Future[Unit] = {
    val result = configManager.get(path, id)
    result onComplete {
      case Success(configDataOpt) => replyTo ! GetResult(path, id, Success(configDataOpt))
      case Failure(ex)            => replyTo ! GetResult(path, id, Failure(ex))
    }
    result.map(_ => ())
  }

  def handleExistsRequest(replyTo: ActorRef, path: File): Future[Unit] = {
    val result = configManager.exists(path)
    result onComplete {
      case Success(bool) => replyTo ! ExistsResult(path, Success(bool))
      case Failure(ex)   => replyTo ! ExistsResult(path, Failure(ex))
    }
    result.map(_ => ())
  }

  def handleDeleteRequest(replyTo: ActorRef, path: File, comment: String): Future[Unit] = {
    unitReply(replyTo, path, configManager.delete(path, comment))
  }

  def handleListRequest(replyTo: ActorRef): Future[Unit] = {
    val result = configManager.list()
    result onComplete {
      case Success(list) => replyTo ! ListResult(Success(list))
      case Failure(ex)   => replyTo ! ListResult(Failure(ex))
    }
    result.map(_ => ())
  }

  def handleHistoryRequest(replyTo: ActorRef, path: File, maxResults: Int = Int.MaxValue): Future[Unit] = {
    val result = configManager.history(path, maxResults)
    result onComplete {
      case Success(list) => replyTo ! HistoryResult(path, Success(list))
      case Failure(ex)   => replyTo ! HistoryResult(path, Failure(ex))
    }
    result.map(_ => ())
  }

  def handleSetDefaultRequest(replyTo: ActorRef, path: File, id: Option[ConfigId]): Future[Unit] = {
    unitReply(replyTo, path, configManager.setDefault(path, id))
  }

  def handleResetDefaultRequest(replyTo: ActorRef, path: File): Future[Unit] = {
    unitReply(replyTo, path, configManager.resetDefault(path))
  }

  def handleGetDefaultRequest(replyTo: ActorRef, path: File): Future[Unit] = {
    val result = configManager.getDefault(path)
    result onComplete {
      case Success(configDataOpt) => replyTo ! GetResult(path, None, Success(configDataOpt))
      case Failure(ex)            => replyTo ! GetResult(path, None, Failure(ex))
    }
    result.map(_ => ())
  }

  private def unitReply(replyTo: ActorRef, path: File, result: Future[Unit]): Future[Unit] = {
    result onComplete {
      case Success(u)  => replyTo ! UnitResult(path, Success(u))
      case Failure(ex) => replyTo ! UnitResult(path, Failure(ex))
    }
    result.map(_ => ())
  }
}
