package org.tmt.csw.cs.akka

import akka.actor._
import org.tmt.csw.cs.api._
import org.tmt.csw.cs.core.git.GitConfigManager
import org.tmt.csw.cs.api.ConfigFileHistory
import org.tmt.csw.cs.api.ConfigFileInfo
import java.io.File

// Messages received by the Config Service actor
sealed trait ConfigServiceRequest
case class CreateRequest(path: String, configData: ConfigData, comment: String = "") extends ConfigServiceRequest
case class UpdateRequest(path: String, configData: ConfigData, comment: String = "") extends ConfigServiceRequest
case class GetRequest(path: String, id: Option[ConfigId] = None) extends ConfigServiceRequest
case class ExistsRequest(path: String) extends ConfigServiceRequest
case class DeleteRequest(path: String, comment: String = "deleted") extends ConfigServiceRequest
case class ListRequest() extends ConfigServiceRequest
case class HistoryRequest(path: String) extends ConfigServiceRequest

// Result messages sent by the Config Service actor
sealed trait ConfigServiceResult
case class CreateResult(result: ConfigId) extends ConfigServiceResult
case class UpdateResult(result: ConfigId) extends ConfigServiceResult
case class GetResult(result: Option[ConfigData]) extends ConfigServiceResult
case class ExistsResult(result: Boolean) extends ConfigServiceResult
case object DeleteResult extends ConfigServiceResult
case class ListResult(result: List[ConfigFileInfo]) extends ConfigServiceResult
case class HistoryResult(result: List[ConfigFileHistory]) extends ConfigServiceResult


object ConfigServiceActor {

  def apply(configManager: ConfigManager) : ConfigServiceActor = new ConfigServiceActor(Some(configManager))

  def apply(gitLocalRepository: File, gitMainRepository: String) : ConfigServiceActor
    = new ConfigServiceActor(Some(GitConfigManager(gitLocalRepository, gitMainRepository)))

  def apply() : ConfigServiceActor = new ConfigServiceActor(None)

  /**
   * Returns the default config manager, using the given settings
   * @param settings read from resources/reference.conf
   */
  private def defaultConfigManager(settings: Settings) : ConfigManager = {
    GitConfigManager(settings.gitLocalRepository, settings.gitMainRepository)
  }
}

/**
 * An Akka actor class implementing the Config Service.
 * @param configManagerOpt specify the configManager to use for tests, use None for production to get default
 */
class ConfigServiceActor(configManagerOpt: Option[ConfigManager]) extends Actor {

  val configManager = {
    configManagerOpt match {
      case Some(m) => m
      case None => ConfigServiceActor.defaultConfigManager(Settings(context.system))
    }
  }

  def receive = {
    case request: ConfigServiceRequest => reply(sender, request)
    case _ => sender ! Status.Failure(new IllegalArgumentException)
  }

  /**
   * Answers the sender with the requested results, or with an exception, if there is an error
   * @param sender the actor that made the request
   * @param request the request
   */
  def reply(sender: ActorRef, request: ConfigServiceRequest) {
    try {
      request match {
        case CreateRequest(path, configData, comment) => sender ! CreateResult(configManager.create(path, configData, comment))
        case UpdateRequest(path, configData, comment) => sender ! UpdateResult(configManager.update(path, configData, comment))
        case GetRequest(path, id) => sender ! GetResult(configManager.get(path, id))
        case ExistsRequest(path) => sender ! ExistsResult(configManager.exists(path))
        case DeleteRequest(path, comment) => configManager.delete(path, comment); sender ! DeleteResult
        case ListRequest() => sender ! ListResult(configManager.list())
        case HistoryRequest(path) => sender ! HistoryResult(configManager.history(path))
      }
    } catch {
      case e: Exception =>
        sender ! Status.Failure(e)
    }
  }
}