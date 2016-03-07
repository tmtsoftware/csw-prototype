package csw.services.cs.akka

import java.io.File

import akka.actor._
import akka.util.Timeout
import csw.services.cs.core.git.GitConfigManager
import csw.services.cs.core.{ConfigFileHistory, _}
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.{ServiceRef, LocationService, ServiceType, ServiceId}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * Config service actor.
 *
 * Note: Only one instance of this actor should exist for a given local Git repository.
 *
 * In this implementation, you should have a single config service actor managing a
 * queue of commands that work on a single local repository, one command at a time.
 * (This is enforced here by waiting for each command to complete before taking the
 * next one from the queue.)
 * This is because the current implementation reads and writes file to the local Git working directory.
 * This has the advantage of being a cache for files, so they don't always have to
 * be copied from the server. While it might be possible to avoid reading and writing files
 * in the working directory using lower level JGit commands, it would still be necessary to
 * have a single config service actor per local repository to avoid potential concurrency
 * issues (Thread-safety might work within the JVM, but not with multiple applications at once).
 */
object ConfigServiceActor {

  // Messages received by this actor
  sealed trait ConfigServiceRequest

  case class CreateRequest(path: File, configData: ConfigData, oversize: Boolean, comment: String = "") extends ConfigServiceRequest

  case class UpdateRequest(path: File, configData: ConfigData, comment: String = "") extends ConfigServiceRequest

  case class CreateOrUpdateRequest(path: File, configData: ConfigData, oversize: Boolean, comment: String = "") extends ConfigServiceRequest

  case class GetRequest(path: File, id: Option[ConfigId] = None) extends ConfigServiceRequest

  case class ExistsRequest(path: File) extends ConfigServiceRequest

  case class DeleteRequest(path: File, comment: String = "deleted") extends ConfigServiceRequest

  case object ListRequest extends ConfigServiceRequest

  case class HistoryRequest(path: File, maxResults: Int = Int.MaxValue) extends ConfigServiceRequest

  case class SetDefaultRequest(path: File, id: Option[ConfigId])

  case class ResetDefaultRequest(path: File)

  case class GetDefaultRequest(path: File)

  // Request config service actor to register with the location service
  case object RegisterWithLocationService extends ConfigServiceRequest

  // Reply messages (The final arguments, wrapped in Try[], give the actual results)
  sealed trait ConfigServiceResult

  case class CreateOrUpdateResult(path: File, configId: Try[ConfigId]) extends ConfigServiceResult

  case class GetResult(path: File, id: Option[ConfigId], configData: Try[Option[ConfigData]]) extends ConfigServiceResult

  case class ExistsResult(path: File, exists: Try[Boolean]) extends ConfigServiceResult

  case class ListResult(list: Try[List[ConfigFileInfo]]) extends ConfigServiceResult

  case class HistoryResult(path: File, history: Try[List[ConfigFileHistory]]) extends ConfigServiceResult

  // Reply message for operations that don't return a value (Delete, SetDefault, ResetDefault)
  case class UnitResult(path: File, status: Try[Unit]) extends ConfigServiceResult

  /**
   * Use this Props instance to initialize with the given ConfigManager
   */
  def props(configManager: ConfigManager): Props =
    Props(classOf[ConfigServiceActor], configManager)

  /**
   * Returns the default config manager, using the configured settings (see resources/reference.conf).
   * @param system the caller's actor system, used to access the settings
   */
  def defaultConfigManager(implicit system: ActorSystem): ConfigManager = {
    val settings = ConfigServiceSettings(system)
    settings.getConfigManager
  }

  /**
   * Convenience method that gets the config service actor with the matching name
   * from the location service.
   * @param name the name of the config service (set in the config file, property csw.services.cs.name)
   * @param system the actor system
   * @return a future reference to the named config service actor
   */
  def locateConfigService(name: String)(implicit system: ActorSystem): Future[ActorRef] = {
    import system.dispatcher
    val serviceId = ServiceId(name, ServiceType.Service)
    val serviceRef = ServiceRef(serviceId, AkkaType)
    implicit val timeout: Timeout = 60.seconds
    LocationService.resolve(Set(serviceRef)).map { servicesReady ⇒
      servicesReady.services(serviceRef).actorRefOpt.get
    }
  }
}

/**
 * An Akka actor class implementing the Config Service.
 * @param configManager the configManager to use
 */
class ConfigServiceActor(configManager: ConfigManager) extends Actor with ActorLogging {

  import context.dispatcher
  import csw.services.cs.akka.ConfigServiceActor._

  // timeout for blocking wait (used to make sure local Git repo working dir access is not concurrent)
  val timeout = 60.seconds

  log.info("Started config service")

  // Registers with the location service
  def registerWithLocationService(): Unit = {
    val serviceId = ServiceId(configManager.name, ServiceType.Service)
    LocationService.registerAkkaService(serviceId, self)(context.system)
  }

  override def receive: Receive = {
    case CreateRequest(path, configData, oversize, comment) ⇒ handleCreateRequest(sender(), path, configData, oversize, comment)
    case UpdateRequest(path, configData, comment) ⇒ handleUpdateRequest(sender(), path, configData, comment)
    case CreateOrUpdateRequest(path, configData, oversize, comment) ⇒ handleCreateOrUpdateRequest(sender(), path, configData, oversize, comment)
    case GetRequest(path, id) ⇒ handleGetRequest(sender(), path, id)
    case ExistsRequest(path) ⇒ handleExistsRequest(sender(), path)
    case DeleteRequest(path, comment) ⇒ handleDeleteRequest(sender(), path, comment)
    case ListRequest ⇒ handleListRequest(sender())
    case HistoryRequest(path, maxResults) ⇒ handleHistoryRequest(sender(), path, maxResults)
    case SetDefaultRequest(path, id) ⇒ handleSetDefaultRequest(sender(), path, id)
    case ResetDefaultRequest(path) ⇒ handleResetDefaultRequest(sender(), path)
    case GetDefaultRequest(path) ⇒ handleGetDefaultRequest(sender(), path)
    case RegisterWithLocationService ⇒ registerWithLocationService()

    case x ⇒ log.error(s"Received unknown message $x from ${sender()}")
  }

  def handleCreateRequest(replyTo: ActorRef, path: File, configData: ConfigData, oversize: Boolean, comment: String): Unit = {
    val result = configManager.create(path, configData, oversize, comment)
    result onComplete {
      case Success(configId) ⇒ replyTo ! CreateOrUpdateResult(path, Success(configId))
      case Failure(ex)       ⇒ replyTo ! CreateOrUpdateResult(path, Failure(ex))
    }
  }

  def handleUpdateRequest(replyTo: ActorRef, path: File, configData: ConfigData, comment: String): Unit = {
    val result = configManager.update(path, configData, comment)
    result onComplete {
      case Success(configId) ⇒ replyTo ! CreateOrUpdateResult(path, Success(configId))
      case Failure(ex)       ⇒ replyTo ! CreateOrUpdateResult(path, Failure(ex))
    }
  }

  def handleCreateOrUpdateRequest(replyTo: ActorRef, path: File, configData: ConfigData, oversize: Boolean, comment: String): Unit = {
    val result = configManager.createOrUpdate(path, configData, oversize, comment)
    result onComplete {
      case Success(configId) ⇒ replyTo ! CreateOrUpdateResult(path, Success(configId))
      case Failure(ex)       ⇒ replyTo ! CreateOrUpdateResult(path, Failure(ex))
    }
  }

  def handleGetRequest(replyTo: ActorRef, path: File, id: Option[ConfigId]): Unit = {
    val result = configManager.get(path, id)
    result onComplete {
      case Success(configDataOpt) ⇒ replyTo ! GetResult(path, id, Success(configDataOpt))
      case Failure(ex)            ⇒ replyTo ! GetResult(path, id, Failure(ex))
    }
  }

  def handleExistsRequest(replyTo: ActorRef, path: File): Unit = {
    val result = configManager.exists(path)
    result onComplete {
      case Success(bool) ⇒ replyTo ! ExistsResult(path, Success(bool))
      case Failure(ex)   ⇒ replyTo ! ExistsResult(path, Failure(ex))
    }
  }

  def handleDeleteRequest(replyTo: ActorRef, path: File, comment: String): Unit = {
    unitReply(replyTo, path, configManager.delete(path, comment))
  }

  def handleListRequest(replyTo: ActorRef): Unit = {
    val result = configManager.list()
    result onComplete {
      case Success(list) ⇒ replyTo ! ListResult(Success(list))
      case Failure(ex)   ⇒ replyTo ! ListResult(Failure(ex))
    }
  }

  def handleHistoryRequest(replyTo: ActorRef, path: File, maxResults: Int = Int.MaxValue): Unit = {
    val result = configManager.history(path, maxResults)
    result onComplete {
      case Success(list) ⇒ replyTo ! HistoryResult(path, Success(list))
      case Failure(ex)   ⇒ replyTo ! HistoryResult(path, Failure(ex))
    }
  }

  def handleSetDefaultRequest(replyTo: ActorRef, path: File, id: Option[ConfigId]): Unit = {
    unitReply(replyTo, path, configManager.setDefault(path, id))
  }

  def handleResetDefaultRequest(replyTo: ActorRef, path: File): Unit = {
    unitReply(replyTo, path, configManager.resetDefault(path))
  }

  def handleGetDefaultRequest(replyTo: ActorRef, path: File): Unit = {
    val result = configManager.getDefault(path)
    result onComplete {
      case Success(configDataOpt) ⇒ replyTo ! GetResult(path, None, Success(configDataOpt))
      case Failure(ex)            ⇒ replyTo ! GetResult(path, None, Failure(ex))
    }
  }

  private def unitReply(replyTo: ActorRef, path: File, result: Future[Unit]): Unit = {
    result onComplete {
      case Success(u)  ⇒ replyTo ! UnitResult(path, Success(u))
      case Failure(ex) ⇒ replyTo ! UnitResult(path, Failure(ex))
    }
  }
}
