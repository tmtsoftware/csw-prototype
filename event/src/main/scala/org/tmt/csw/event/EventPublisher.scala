package org.tmt.csw.event

import akka.actor.{ActorLogging, Actor}

/**
 * Adds the ability to publish events.
 */
trait EventPublisher {
  this: Actor with ActorLogging =>

  // Connect to Hornetq server
  private val (sf, session) = connectToHornetQ(context.system)
  private val producer = session.createProducer()

  /**
   * Publishes the given event to the given channel.
   * @param channel The channel (Hornetq address) to publish on.
   * @param event the event to publish
   */
  def publish(channel: String, event: Event): Unit = {
    val message = session.createMessage(false)
    message.getBodyBuffer.writeUTF(event.toString)
    message.setExpiration(System.currentTimeMillis() + 1000) // expire after 1 second
    producer.send(channel, message)
  }

  override def postStop(): Unit = sf.close()
}
