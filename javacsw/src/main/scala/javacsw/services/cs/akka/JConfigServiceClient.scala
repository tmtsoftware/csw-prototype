package javacsw.services.cs.akka

import javacsw.services.cs.core.{JBlockingConfigManagerImpl, JConfigManagerImpl}

import akka.actor.ActorRefFactory
import csw.services.cs.akka.ConfigServiceClient

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
