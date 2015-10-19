package csw.services.event

import akka.actor.{ ActorLogging, Actor }
import csw.util.cfg.ConfigSerializer

/**
 * Adds the ability to publish events.
 */
trait EventPublisher extends Hq {
  this: Actor with ActorLogging ⇒

  private val producer = hq.session.createProducer()

  /**
   * Publishes the given event (channel is the event prefix).
   * @param event the event to publish
   */
  def publish(event: Event): Unit = {
    import ConfigSerializer._
    val message = hq.session.createMessage(false)
    val buf = message.getBodyBuffer
    buf.clear()
    buf.writeBytes(write(event))
    message.setExpiration(System.currentTimeMillis() + 1000) // expire after 1 second
    producer.send(event.prefix, message)
  }

  override def postStop(): Unit = hq.close()
}
