package org.tmt.csw.event

import akka.actor.{ActorLogging, Actor}

/**
 * Adds the ability to publish events.
 */
trait EventPublisher {
  this: Actor with ActorLogging =>

  // Connect to Hornetq server
  private val (sf, session) = connectToHornetQ()
  private val producer = session.createProducer()

  /**
   * Publishes the given event to the given channel.
   * @param channel The channel (Hornetq address) to publish on.
   * @param event the event to publish
   */
  def publish(channel: String, event: Event): Unit = {
    val message = session.createMessage(false).putStringProperty(propName, event.toString)
    message.setExpiration(System.currentTimeMillis() + 5000) // expire after 5 seconds
    producer.send(channel, message)
  }

  /**
   * Close the session, clean up resources
   */
  def closeSession(): Unit = sf.close()
}
