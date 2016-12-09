package javacsw.services.cs.akka

import java.io.File
import java.util.Optional
import java.util.concurrent.CompletableFuture
import javacsw.services.cs.core.{JBlockingConfigManager, JConfigManager}

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import csw.services.cs.akka.{ConfigServiceActor, ConfigServiceClient}
import csw.services.cs.core.{ConfigData, ConfigId}

import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * A non-blocking Java client for the config service
 *
 * @param client  the scala implementation of the cs client
 * @param context the akka actor context (system or context)
 */
class JConfigServiceClient(client: ConfigServiceClient)(implicit context: ActorRefFactory) extends JConfigManager(client)

/**
 * Static utility methods for use by Java applications
 */
object JConfigServiceClient {
  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   *
   * @param path    the path of the file in the config service
   * @param id      optional id of a specific version of the file
   * @param system  actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future contents of the file as a ConfigData object, if found
   */
  def getFromConfigService(path: File, id: Optional[ConfigId], system: ActorSystem, timeout: Timeout): CompletableFuture[Optional[ConfigData]] = {
    import system.dispatcher
    ConfigServiceClient.getFromConfigService(path, id.asScala)(system, timeout).map(_.asJava).toJava.toCompletableFuture
  }

  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   *
   * @param path    the path of the file in the config service
   * @param id      optional id of a specific version of the file
   * @param system  actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future contents of the file as a string, if the file was found
   */
  def getStringFromConfigService(path: File, id: Optional[ConfigId], system: ActorSystem, timeout: Timeout): CompletableFuture[Optional[String]] = {
    import system.dispatcher
    ConfigServiceClient.getStringFromConfigService(path, id.asScala)(system, timeout).map(_.asJava).toJava.toCompletableFuture
  }

  /**
   * Convenience method that gets a Typesafe Config from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the given file using a config service client.
   * Finally, the file contents is parsed as a Typesafe config file and the
   * Config object returned.
   *
   * @param path     the path of the file in the config service
   * @param id       optional id of a specific version of the file
   * @param resource optional resource file to use in case the file can't be retrieved from the config service for some reason
   * @param system   actor system needed to access config service
   * @param timeout  time to wait for a reply
   * @return the future config, parsed from the file
   */
  def getConfigFromConfigService(path: File, id: Optional[ConfigId], resource: Optional[File],
                                 system: ActorSystem, timeout: Timeout): CompletableFuture[Optional[Config]] = {
    import system.dispatcher
    ConfigServiceClient.getConfigFromConfigService(path, id.asScala, resource.asScala)(system, timeout).map(_.asJava).toJava.toCompletableFuture
  }

  /**
   * Convenience method that stores the contents of a given Config object in the config service.
   *
   * @param path    the path the file should have in the config service
   * @param config  the config to store
   * @param system  actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future contents of the file as a ConfigData object, if found
   */
  def saveConfigToConfigService(path: File, config: Config, system: ActorSystem, timeout: Timeout): CompletableFuture[ConfigId] =
    ConfigServiceClient.saveConfigToConfigService(path, config)(system, timeout).toJava.toCompletableFuture

}

/**
 * A blocking Java client for the config service
 *
 * @param client  the scala implementation of the cs client
 * @param context the akka actor context (system or context)
 */
class JBlockingConfigServiceClient(client: ConfigServiceClient)(implicit context: ActorRefFactory) extends JBlockingConfigManager(client)

/**
 * Contains Java API helper methods related to the Scala ConfigServiceActor class
 */
object JConfigService {
  /**
   * Java API: Locate the config service with the given name using the location service.
   *
   * @param name   the name the config service was registered with
   * @param system the actor system to use
   * @param timeout amount of time to alow for looking up config service with location service
   * @return the future ActorRef for the config service (May throw an exception if not found)
   */
  def locateConfigService(name: String, system: ActorSystem, timeout: Timeout): CompletableFuture[ActorRef] =
    ConfigServiceActor.locateConfigService(name)(system, timeout).toJava.toCompletableFuture

  /**
   * Java API: Locate the default config service using the location service
   *
   * @param system the actor system to use
   * @return the future ActorRef for the config service (May throw an exception if not found)
   */
  def locateConfigService(system: ActorSystem, timeout: Timeout): CompletableFuture[ActorRef] =
    ConfigServiceActor.locateConfigService()(system, timeout).toJava.toCompletableFuture
}