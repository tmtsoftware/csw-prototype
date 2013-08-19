package org.tmt.csw.cs.akka

import akka.actor._
import org.tmt.csw.cs.api._
import org.tmt.csw.cs.core.git.GitConfigManager
import java.io.File
import ConfigServiceActor._
import java.net.URI


/**
 * Defines apply methods for creating a ConfigServiceActor instance
 */
object ConfigServiceActor {
  // Messages received by the Config Service actor
  sealed trait ConfigServiceRequest
  case class CreateRequest(path: String, configData: ConfigData, comment: String = "") extends ConfigServiceRequest
  case class UpdateRequest(path: String, configData: ConfigData, comment: String = "") extends ConfigServiceRequest
  case class GetRequest(path: String, id: Option[ConfigId] = None) extends ConfigServiceRequest
  case class ExistsRequest(path: String) extends ConfigServiceRequest
  case class DeleteRequest(path: String, comment: String = "deleted") extends ConfigServiceRequest
  case object ListRequest extends ConfigServiceRequest
  case class HistoryRequest(path: String) extends ConfigServiceRequest

  /**
   * Use this Props instance to initialize with the given ConfigManager
   */
  def props(configManager: ConfigManager): Props = Props(classOf[ConfigServiceActor], Some(configManager))

  /**
   * Use this Props instance to initialize with the local repository directory and the path or URI for the main repository
   */
  def props(gitLocalRepository: File, gitMainRepository: URI): Props
  = Props(classOf[ConfigServiceActor], Some(GitConfigManager(gitLocalRepository, gitMainRepository)))

  /**
   * Use this Props instance to initialize using the default Git repository (configured in resources/reference.conf)
   */
  def props(): Props = Props[ConfigServiceActor]

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
class ConfigServiceActor(configManagerOpt: Option[ConfigManager] = None) extends Actor with ActorLogging {

  // The ConfigManager instance used to access the Git repository
  val configManager = {
    configManagerOpt match {
      case Some(m) => m
      case None => defaultConfigManager(Settings(context.system))
    }
  }

  /**
   * Receive actor messages and send replies (via reply method).
   * The senders should use "?" (ask) and the response will be a Future containing the result (or an exception).
   */
  override def receive: Receive = {
    case request: ConfigServiceRequest => reply(sender, request)
    case _ => sender ! Status.Failure(new IllegalArgumentException)
  }

  /**
   * Answers the sender with the requested results, or with an exception, if there is an error
   * @param sender the actor that made the request
   * @param request the request
   */
  def reply(sender: ActorRef, request: ConfigServiceRequest): Unit = {
    log.debug(s"Replying to request: ${request.getClass.getSimpleName}")
    try {
      request match {
        case CreateRequest(path, configData, comment) => sender ! configManager.create(path, configData, comment)
        case UpdateRequest(path, configData, comment) => sender ! configManager.update(path, configData, comment)
        case GetRequest(path, id) => sender ! configManager.get(path, id)
        case ExistsRequest(path) => sender ! configManager.exists(path)
        case DeleteRequest(path, comment) => sender ! configManager.delete(path, comment)
        case ListRequest => sender ! configManager.list()
        case HistoryRequest(path) => sender ! configManager.history(path)
      }
    } catch {
      case e: Exception =>
        sender ! Status.Failure(e)
    }
  }
}
