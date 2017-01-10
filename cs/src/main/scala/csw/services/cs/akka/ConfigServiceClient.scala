package csw.services.cs.akka

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import csw.services.cs.akka.ConfigServiceActor._
import csw.services.cs.core._

import scala.concurrent.Future

object ConfigServiceClient {
  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   *
   * @param path the path of the file in the config service
   * @param id optional id of a specific version of the file
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future contents of the file as a ConfigData object, if found
   */
  def getFromConfigService(path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Future[Option[ConfigData]] = {
    import system.dispatcher
    val csName = if (system.settings.config.hasPath("csw.services.cs.name"))
      system.settings.config.getString("csw.services.cs.name")
    else ConfigServiceSettings.defaultName

    for {
      cs <- locateConfigService(csName)
      configDataOpt <- ConfigServiceClient(cs, csName).get(path, id)
    } yield configDataOpt
  }

  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   *
   * @param path the path of the file in the config service
   * @param id optional id of a specific version of the file
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future contents of the file as a string, if the file was found
   */
  def getStringFromConfigService(path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Future[Option[String]] = {
    import system.dispatcher
    getFromConfigService(path, id).flatMap { configDataOpt =>
      if (configDataOpt.isDefined)
        configDataOpt.get.toFutureString.map(Some(_))
      else
        Future(None)
    }
  }

  /**
   * Convenience method that gets a Typesafe Config from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the given file using a config service client.
   * Finally, the file contents is parsed as a Typesafe config file and the
   * Config object returned.
   *
   * @param path the path of the file in the config service
   * @param id optional id of a specific version of the file
   * @param resource optional resource file to use in case the file can't be retrieved from the config service for some reason
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future config, parsed from the file
   */
  def getConfigFromConfigService(path: File, id: Option[ConfigId] = None, resource: Option[File] = None)(implicit system: ActorSystem, timeout: Timeout): Future[Option[Config]] = {
    import system.dispatcher

    val log = Logging(system, this.getClass)

    def getFromResource: Option[Config] = try {
      val opt = resource.map(f => ConfigFactory.parseResources(f.getPath))
      log.warning(s"Failed to get $path from config service: using resource $resource")
      opt
    } catch {
      case ex: Exception =>
        log.error(s"Failed to get config from resource: $resource", ex)
        None
    }

    val f = for {
      s <- getStringFromConfigService(path, id)
    } yield {
      s match {
        case Some(x) =>
          val configOpt = Some(ConfigFactory.parseString(x).resolve(ConfigResolveOptions.noSystem()))
          log.info(s"Loaded config from config service file: $path")
          configOpt
        case None => getFromResource
      }
    }
    f.recover {
      case ex =>
        log.error(ex, s"Failed to get $path ($id) from config service")
        getFromResource
    }
  }

  /**
   * Convenience method that stores the contents of a given Config object in the config service.
   *
   * @param path the path the file should have in the config service
   * @param config the config to store
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future contents of the file as a ConfigData object, if found
   */
  def saveConfigToConfigService(path: File, config: Config)(implicit system: ActorSystem, timeout: Timeout): Future[ConfigId] = {
    import system.dispatcher
    val csName = if (system.settings.config.hasPath("csw.services.cs.name"))
      system.settings.config.getString("csw.services.cs.name")
    else ConfigServiceSettings.defaultName

    for {
      cs <- locateConfigService(csName)
      configId <- ConfigServiceClient(cs, csName).createOrUpdate(path, ConfigData(config.root().render()))
    } yield configId
  }

}

/**
 * Adds a convenience layer over the Akka actor interface to the configuration service.
 * Note:Only one instance of this class should exist for a given repository.
 *
 * @param configServiceActor the config service actor reference to use (Get from location service, if needed)
 * @param name               the config service name (defaults to "Config Service")
 * @param system             the caller's actor system
 * @param timeout            amount of time to wait for config service operations to complete
 */
case class ConfigServiceClient(configServiceActor: ActorRef, name: String = "Config Service")(implicit system: ActorSystem, timeout: Timeout) extends ConfigManager {

  //  val settings = ConfigServiceSettings(system)
  //  override val name = settings.name

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

