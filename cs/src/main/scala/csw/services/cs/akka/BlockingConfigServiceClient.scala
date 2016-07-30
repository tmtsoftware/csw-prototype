package csw.services.cs.akka

import java.io.File

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.Config
import csw.services.cs.core.{BlockingConfigManager, ConfigData, ConfigId}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A blocking wrapper for a ConfigServiceClient.
 */
object BlockingConfigServiceClient {
  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   *
   * @param csName the name of the config service (the name it was registered with, from it's config file)
   * @param path the path of the file in the config service
   * @param id optional id of a specific version of the file
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the contents of the file as a ConfigData object, if found
   */
  def getFromConfigService(csName: String, path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Option[ConfigData] =
    Await.result(ConfigServiceClient.getFromConfigService(csName, path, id), timeout.duration)

  /**
   * Convenience method that gets the contents of the given file from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the file using a config service client.
   * (Use only for small files.)
   *
   * @param csName the name of the config service (the name it was registered with, from it's config file)
   * @param path the path of the file in the config service
   * @param id optional id of a specific version of the file
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the contents of the file as a string, if the file was found
   */
  def getStringFromConfigService(csName: String, path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Option[String] =
    Await.result(ConfigServiceClient.getStringFromConfigService(csName, path, id), timeout.duration)

  /**
   * Convenience method that gets a Typesafe Config from the config service
   * by first looking up the config service with the location service and
   * then fetching the contents of the given file using a config service client.
   * Finally, the file contents is parsed as a Typesafe config file and the
   * Config object returned.
   *
   * @param csName the name of the config service (the name it was registered with, from it's config file)
   * @param path the path of the file in the config service
   * @param id optional id of a specific version of the file
   * @param system actor system needed to access config service
   * @param timeout time to wait for a reply
   * @return the future config, parsed from the file
   */
  def getConfigFromConfigService(csName: String, path: File, id: Option[ConfigId] = None)(implicit system: ActorSystem, timeout: Timeout): Option[Config] =
    Await.result(ConfigServiceClient.getConfigFromConfigService(csName, path, id), timeout.duration)
}

/**
 * A blocking wrapper for a ConfigServiceClient.
 */
class BlockingConfigServiceClient(csc: ConfigServiceClient, t: Duration = 30.seconds)(implicit val ctx: ActorRefFactory)
    extends BlockingConfigManager(csc, t)(ctx) {

}
