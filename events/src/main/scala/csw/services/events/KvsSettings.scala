package csw.services.events

import akka.actor._
import com.typesafe.config.Config

object KvsSettings extends ExtensionId[KvsSettings] with ExtensionIdProvider {
  override def lookup(): KvsSettings.type = KvsSettings

  override def createExtension(system: ExtendedActorSystem): KvsSettings = new KvsSettings(system.settings.config)

  def getKvsSettings(system: ActorSystem): KvsSettings = KvsSettings(system)
}

case class KvsSettings(redisHostname: String, redisPort: Int) extends Extension {
  def this(config: Config) = this(config.getString("csw.redis.hostname"), config.getInt("csw.redis.port"))
}
