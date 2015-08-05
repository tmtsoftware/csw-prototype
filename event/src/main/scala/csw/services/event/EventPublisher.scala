package csw.services.event

import akka.actor.{ ActorLogging, Actor }

/**
 * Adds the ability to publish events.
 */
trait EventPublisher extends Hq {
  this: Actor with ActorLogging ⇒

  private val producer = hq.session.createProducer()

  /**
   * Publishes the given event to the given channel.
   * @param channel The channel (Hornetq address) to publish on.
   * @param event the event to publish
   */
  def publish(channel: String, event: Event): Unit = {
    val message = hq.session.createMessage(false)
    val buf = message.getBodyBuffer
    buf.clear()
    buf.writeBytes(event.toBinary)
    message.setExpiration(System.currentTimeMillis() + 1000) // expire after 1 second
    producer.send(channel, message)
  }

  override def postStop(): Unit = hq.close()
}
