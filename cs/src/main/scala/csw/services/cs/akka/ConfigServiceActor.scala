package csw.services.cs.akka

import java.io.File

import akka.actor._
import csw.services.cs.core.{ConfigFileHistory, _}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


/**
 * Defines apply methods for creating a ConfigServiceActor instance
 */
object ConfigServiceActor {

  // Messages received by this actor
  sealed trait ConfigServiceRequest

  case class CreateRequest(path: File, configData: ConfigData, oversize: Boolean, comment: String = "") extends ConfigServiceRequest

  case class UpdateRequest(path: File, configData: ConfigData, comment: String = "") extends ConfigServiceRequest

  case class GetRequest(path: File, id: Option[ConfigId] = None) extends ConfigServiceRequest

  case class ExistsRequest(path: File) extends ConfigServiceRequest

  case class DeleteRequest(path: File, comment: String = "deleted") extends ConfigServiceRequest

  case object ListRequest extends ConfigServiceRequest

  case class HistoryRequest(path: File) extends ConfigServiceRequest

  // Reply messages (The final arguments, wrapped in Try[], give the actual results)
  sealed trait ConfigServiceResult

  case class CreateResult(path: File, configId: Try[ConfigId]) extends ConfigServiceResult

  case class UpdateResult(path: File, configId: Try[ConfigId]) extends ConfigServiceResult

  case class GetResult(path: File, id: Option[ConfigId], configData: Try[Option[ConfigData]]) extends ConfigServiceResult

  case class ExistsResult(path: File, exists: Try[Boolean]) extends ConfigServiceResult

  case class DeleteResult(path: File, status: Try[Unit]) extends ConfigServiceResult

  case class ListResult(list: Try[List[ConfigFileInfo]]) extends ConfigServiceResult

  case class HistoryResult(path: File, history: Try[List[ConfigFileHistory]]) extends ConfigServiceResult

  /**
   * Use this Props instance to initialize with the given ConfigManager
   */
  def props(configManager: NonBlockingConfigManager): Props =
    Props(classOf[ConfigServiceActor], configManager)

  /**
   * Returns the default config manager, using the configured settings (see resources/reference.conf).
   * @param system the caller's actor system, used to access the settings
   */
  def defaultConfigManager(system: ActorSystem): Future[NonBlockingConfigManager] = {
    import system.dispatcher
    val settings = Settings(system)
    NonBlockingGitConfigManager(settings.gitLocalRepository, settings.gitMainRepository, settings.gitOversizeStorage)
  }
}

/**
 * An Akka actor class implementing the Config Service.
 * @param configManager the configManager to use (See [[ConfigServiceActor.defaultConfigManager]])
 */
class ConfigServiceActor(configManager: NonBlockingConfigManager) extends Actor with ActorLogging {

  import context.dispatcher
  import csw.services.cs.akka.ConfigServiceActor._

  override def receive: Receive = {
    case CreateRequest(path, configData, oversize, comment) => handleCreateRequest(sender(), path, configData, oversize, comment)
    case UpdateRequest(path, configData, comment) => handleUpdateRequest(sender(), path, configData, comment)
    case GetRequest(path, id) => handleGetRequest(sender(), path, id)
    case ExistsRequest(path) => handleExistsRequest(sender(), path)
    case DeleteRequest(path, comment) => handleDeleteRequest(sender(), path, comment)
    case ListRequest => handleListRequest(sender())
    case HistoryRequest(path) => handleHistoryRequest(sender(), path)

    case x => log.error(s"Received unknown message $x from ${sender()}")
  }

  def handleCreateRequest(replyTo: ActorRef, path: File, configData: ConfigData, oversize: Boolean, comment: String): Unit =
    configManager.create(path, configData, oversize, comment) onComplete {
      case Success(configId) => replyTo ! CreateResult(path, Success(configId))
      case Failure(ex) => replyTo ! CreateResult(path, Failure(ex))
    }

  def handleUpdateRequest(replyTo: ActorRef, path: File, configData: ConfigData, comment: String): Unit =
    configManager.update(path, configData, comment) onComplete {
      case Success(configId) => replyTo ! UpdateResult(path, Success(configId))
      case Failure(ex) => replyTo ! UpdateResult(path, Failure(ex))
    }

  def handleGetRequest(replyTo: ActorRef, path: File, id: Option[ConfigId]): Unit = {
    configManager.get(path, id) onComplete {
      case Success(configDataOpt) => replyTo ! GetResult(path, id, Success(configDataOpt))
      case Failure(ex) => replyTo ! GetResult(path, id, Failure(ex))
    }
  }


  def handleExistsRequest(replyTo: ActorRef, path: File): Unit =
    configManager.exists(path) onComplete {
      case Success(bool) => replyTo ! ExistsResult(path, Success(bool))
      case Failure(ex) => replyTo ! ExistsResult(path, Failure(ex))
    }


  def handleDeleteRequest(replyTo: ActorRef, path: File, comment: String): Unit =
    configManager.delete(path, comment) onComplete {
      case Success(u) => replyTo ! DeleteResult(path, Success(u))
      case Failure(ex) => replyTo ! DeleteResult(path, Failure(ex))
    }

  def handleListRequest(replyTo: ActorRef): Unit =
    configManager.list() onComplete {
      case Success(list) => replyTo ! ListResult(Success(list))
      case Failure(ex) => replyTo ! ListResult(Failure(ex))
    }

  def handleHistoryRequest(replyTo: ActorRef, path: File): Unit =
    configManager.history(path) onComplete {
      case Success(list) => replyTo ! HistoryResult(path, Success(list))
      case Failure(ex) => replyTo ! HistoryResult(path, Failure(ex))
    }
}
