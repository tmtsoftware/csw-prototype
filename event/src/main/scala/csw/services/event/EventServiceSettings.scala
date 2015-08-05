package csw.services.event

import akka.actor.{ ActorSystem, Extension, ExtensionKey }
import net.ceedubs.ficus.Ficus._

object EventServiceSettings extends ExtensionKey[EventServiceSettings]

/**
 * The configuration settings for the event service
 */
case class EventServiceSettings(system: ActorSystem) extends Extension {
  private val config = system.settings.config
  val eventServiceHostname: Option[String] = config.as[Option[String]]("csw.event-service.hostname")
  val eventServicePort: Option[Int] = config.as[Option[Int]]("csw.event-service.port")
  val useEmbeddedHornetq: Boolean = config.as[Option[Boolean]]("csw.event-service.use-embedded-hornetq").getOrElse(false)
}

