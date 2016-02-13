package csw.services.cs.akka

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigResolveOptions, ConfigFactory}
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.core._

import scala.concurrent.Future

object ConfigServiceClient {
  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   */
  def getStringFromConfigService(settings: ConfigServiceSettings, path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Future[String] = {
    import system.dispatcher
    for {
      cs ← locateConfigService(settings.name)
      configDataOpt ← ConfigServiceClient(cs).get(path, id)
      s ← configDataOpt.get.toFutureString
    } yield s
  }

  /**
   * Convenience method that gets a Typesafe Config from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the given file using a config service client.
   * Finally, the file contents is parsed as a Typesafe config file and the
   * Config object returned.
   */
  def getConfigFromConfigService(settings: ConfigServiceSettings, path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Future[Config] = {
    import system.dispatcher
    for {
      s ← getStringFromConfigService(settings, path, id)
    } yield ConfigFactory.parseString(s).resolve(ConfigResolveOptions.noSystem())
  }
}

/**
 * Adds a convenience layer over the Akka actor interface to the configuration service.
 * Note:Only one instance of this class should exist for a given local Git repository.
 *
 * @param configServiceActor the config service actor reference to use
 * @param system the caller's actor system
 * @param timeout amount of time to wait for config service operations to complete
 */
case class ConfigServiceClient(configServiceActor: ActorRef)(implicit system: ActorSystem, timeout: Timeout) extends ConfigManager {

  val settings = ConfigServiceSettings(system)
  override val name = settings.name

  import system.dispatcher

  override def create(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] =
    (configServiceActor ? CreateRequest(path, configData, oversize, comment))
      .mapTo[CreateOrUpdateResult].map(_.configId.get)

  override def update(path: File, configData: ConfigData, comment: String): Future[ConfigId] =
    (configServiceActor ? UpdateRequest(path, configData, comment)).
      mapTo[CreateOrUpdateResult].map(_.configId.get)

  override def createOrUpdate(path: File, configData: ConfigData, oversize: Boolean, comment: String): Future[ConfigId] =
    (configServiceActor ? CreateOrUpdateRequest(path, configData, oversize, comment))
      .mapTo[CreateOrUpdateResult].map(_.configId.get)

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

