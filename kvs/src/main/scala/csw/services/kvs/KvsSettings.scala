package csw.services.kvs

import akka.actor.{ActorSystem, Extension, ExtensionKey}

object KvsSettings extends ExtensionKey[KvsSettings]

/**
 * The configuration settings for the kvs
 */
case class KvsSettings(system: ActorSystem) extends Extension {
  val redisHostname: String = system.settings.config getString "csw.redis.hostname"
  val redisPort: Int = system.settings.config getInt "csw.redis.port"
}

