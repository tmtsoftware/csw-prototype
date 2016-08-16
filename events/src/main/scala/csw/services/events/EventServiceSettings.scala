package csw.services.events

import akka.actor._
import com.typesafe.config.Config

object EventServiceSettings extends ExtensionId[EventServiceSettings] with ExtensionIdProvider {
  override def lookup(): EventServiceSettings.type = EventServiceSettings

  override def createExtension(system: ExtendedActorSystem): EventServiceSettings = new EventServiceSettings(system.settings.config)

  def getEventServiceSettings(system: ActorSystem): EventServiceSettings = EventServiceSettings(system)
}

/**
 * Describes the Redis connection information required by the Event Service.
 * @param redisHostname the host where Redis is running
 * @param redisPort the port for Redis
 */
case class EventServiceSettings(redisHostname: String, redisPort: Int) extends Extension {
  def this(config: Config) = this(config.getString("csw.redis.hostname"), config.getInt("csw.redis.port"))
}
