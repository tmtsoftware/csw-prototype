package org.tmt.csw.cs.akka

import akka.actor.{Status, ActorRef, Actor}
import org.tmt.csw.cs.api._
import org.tmt.csw.cs.api.ConfigFileHistory
import org.tmt.csw.cs.api.ConfigFileInfo

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
case class DeleteResult(result: Unit) extends ConfigServiceResult
case class ListResult(result: List[ConfigFileInfo]) extends ConfigServiceResult
case class HistoryResult(result: List[ConfigFileHistory]) extends ConfigServiceResult


/**
 * An Akka actor class implementing the Config Service.
 * <p>
 * Note: To pass in argument to the constructor, use:
 * val configServiceActor = system.actorOf(Props(new ConfigServiceActor(configManager)), name = "configManager")
 */
class ConfigServiceActor(configManager: ConfigManager) extends Actor {

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
        case DeleteRequest(path, comment) => sender ! DeleteResult(configManager.delete(path, comment))
        case ListRequest() => sender ! ListResult(configManager.list())
        case HistoryRequest(path) => sender ! HistoryResult(configManager.history(path))
      }
    } catch {
      case e: Exception =>
        sender ! akka.actor.Status.Failure(e)
        throw e
    }
  }
}
