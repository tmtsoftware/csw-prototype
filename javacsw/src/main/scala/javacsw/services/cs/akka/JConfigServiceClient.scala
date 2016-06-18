package javacsw.services.cs.akka

import java.util.concurrent.CompletableFuture
import javacsw.services.cs.core.{JBlockingConfigManagerImpl, JConfigManagerImpl}

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import csw.services.cs.akka.{ConfigServiceActor, ConfigServiceClient}

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration._

/**
 * A non-blocking Java client for the config service
 * @param client the scala implementation of the cs client
 * @param context the akka actor context (system or context)
 */
class JConfigServiceClient(client: ConfigServiceClient)(implicit context: ActorRefFactory) extends JConfigManagerImpl(client)

/**
 * A blocking Java client for the config service
 * @param client the scala implementation of the cs client
 * @param context the akka actor context (system or context)
 */
class JBlockingConfigServiceClient(client: ConfigServiceClient)(implicit context: ActorRefFactory) extends JBlockingConfigManagerImpl(client)

/**
 * Contains Java API helper methods related to the Scala [[csw.services.cs.akka.ConfigServiceActor]] class
 */
object JConfigService {
  val timeout = 60.seconds

  /**
   * Java API: Locate the config service with the given name using the location service.
   * @param name the name the config service was registered with
   * @param system the actor system to use
   * @return the future ActorRef for the config service (May throw an exception if not found)
   */
  def locateConfigService(name: String, system: ActorSystem): CompletableFuture[ActorRef] =
    ConfigServiceActor.locateConfigService(name)(system).toJava.toCompletableFuture

  /**
   * Java API: Locate the default config service using the location service
   * @param system the actor system to use
   * @return the future ActorRef for the config service (May throw an exception if not found)
   */
  def locateConfigService(system: ActorSystem): CompletableFuture[ActorRef] =
    ConfigServiceActor.locateConfigService()(system).toJava.toCompletableFuture
}