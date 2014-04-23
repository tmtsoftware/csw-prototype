package org.tmt.csw.event

import akka.actor.{ActorLogging, Actor}
import org.tmt.csw.util.Configuration
import org.hornetq.api.core.client._
import java.util.UUID
import scala.concurrent.Future

/**
 * Adds the ability to subscribe to events.
 * The subscribed actor wil receive Event messages for the given channel.
 */
trait EventSubscriber {
  this: Actor with ActorLogging =>

  private val handler = new MessageHandler() {
    override def onMessage(message: ClientMessage): Unit = {
      val msg = message.getBodyBuffer.readUTF()
      // Use a future to avoid blocking this thread
      Future.successful {
        self ! Configuration(msg)
      }
    }
  }

  // Connect to Hornetq server
  private val (sf, session) = connectToHornetQ(context.system)

  // Unique id for this subscriber
  private val subscriberId = UUID.randomUUID().toString

  // Unique queue name for this subscriber
  private def makeQueueName(channel: String): String = s"$channel-$subscriberId"

  // Local object used to manage a subscription.
  // It creates a queue with a unique name for each channel.
  case class SubscriberInfo(channel: String) {
    val coreSession = sf.createSession(false, false, false)
    val queueName = makeQueueName(channel)
    coreSession.createQueue(channel, queueName, /*, filter */ false)
    coreSession.close()

    val messageConsumer = session.createConsumer(queueName, null, -1, -1, false)
    messageConsumer.setMessageHandler(handler)
  }

  // Maps channel (hornetq address) to SubscriberInfo
  private var map = Map[String, SubscriberInfo]()

  /**
   * Subscribes this actor to events with the given channels.
   *
   * @param channels the channel for the events you want to subscribe to.
   */
  def subscribe(channels: String*): Unit = {
    for(channel <- channels) {
      map += (channel -> SubscriberInfo(channel))
    }
  }

  /**
   * Unsubscribes this actor from events from the given channel.
   *
   * @param channels the top channels for the events you want to unsubscribe from.
   */
  def unsubscribe(channels: String*): Unit = {
//    val coreSession = sf.createSession(false, false, false)
    for(channel <- channels) {
      val info = map(channel)
      map -= channel
      info.messageConsumer.close()
      session.deleteQueue(info.queueName)
    }
//    coreSession.close()
  }

  override def postStop(): Unit = sf.close()
}
