package csw.services.cs.akka

import scala.concurrent.Future
import akka.pattern.ask
import akka.actor.{ ActorSystem, ActorRef }
import akka.util.Timeout
import ConfigServiceActor._
import java.io.File
import csw.services.cs.core._

/**
 * Adds a convenience layer over the Akka actor interface to the configuration service.
 * Note:Only one instance of this class should exist for a given local Git repository.
 *
 * @param system the caller's actor system
 * @param configServiceActor the config service actor reference to use
 * @param name a unique name for this config service
 * @param timeout amount of time to wait for config service operations to complete
 */
case class ConfigServiceClient(system: ActorSystem, configServiceActor: ActorRef, override val name: String)(implicit timeout: Timeout) extends ConfigManager {

  import system.dispatcher

  override def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] =
    (configServiceActor ? CreateRequest(path, configData, oversize, comment))
      .mapTo[CreateResult].map(_.configId.get)

  override def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] =
    (configServiceActor ? UpdateRequest(path, configData, comment)).
      mapTo[UpdateResult].map(_.configId.get)

  override def get(path: File, id: Option[ConfigId] = None): Future[Option[ConfigData]] =
    (configServiceActor ? GetRequest(path, id)).mapTo[GetResult].map(_.configData.get)

  override def exists(path: File): Future[Boolean] =
    (configServiceActor ? ExistsRequest(path)).mapTo[ExistsResult].map(_.exists.get)

  override def delete(path: File, comment: String): Future[Unit] =
    (configServiceActor ? DeleteRequest(path)).mapTo[UnitResult].map(_.status.get)

  override def list(): Future[List[ConfigFileInfo]] =
    (configServiceActor ? ListRequest).mapTo[ListResult].map(_.list.get)

  override def history(path: File, maxResults: Int = Int.MaxValue): Future[List[ConfigFileHistory]] =
    (configServiceActor ? HistoryRequest(path, maxResults)).mapTo[HistoryResult].map(_.history.get)

  override def setDefault(path: File, id: Option[ConfigId]): Future[Unit] =
    (configServiceActor ? SetDefaultRequest(path, id)).mapTo[UnitResult].map(_.status.get)

  override def resetDefault(path: File): Future[Unit] =
    (configServiceActor ? ResetDefaultRequest(path)).mapTo[UnitResult].map(_.status.get)

  override def getDefault(path: File): Future[Option[ConfigData]] =
    (configServiceActor ? GetDefaultRequest(path)).mapTo[GetResult].map(_.configData.get)
}

