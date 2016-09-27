package csw.services.events

import akka.actor.{ActorRef, ActorRefFactory, PoisonPill, Props}
import akka.util.ByteString
import csw.services.events.EventService.EventMonitor
import csw.util.config.ConfigSerializer._
import redis.{ByteStringFormatter, RedisClient}

import scala.concurrent.Future

object EventServiceImpl {
  // Implicit conversion between ByteString and Event, for the Redis API
  implicit val eventFormatter = new ByteStringFormatter[Event] {
    def serialize(e: Event): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): Event = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[Event](ar)
    }
  }

  // Implement value returned from subscribe method
  private[events] case class EventMonitorImpl(actorRef: ActorRef) extends EventMonitor {
    override def stop(): Unit = {
      actorRef ! PoisonPill
    }
  }

  // Actor used to subscribe to events for given prefixes and then notify the actor or call the function
  private object EventMonitorActor {
    def props(subscriber: Option[ActorRef], callback: Option[Event => Unit],
              currentEvents: Future[Seq[Event]], prefixes: String*): Props =
      Props(classOf[EventMonitorActor], subscriber, callback, currentEvents: Future[Seq[Event]], prefixes)
  }

  private class EventMonitorActor(subscriber: Option[ActorRef], callback: Option[Event => Unit],
                                  currentEvents: Future[Seq[Event]], prefixes: String*) extends EventSubscriber {
    import context.dispatcher

    // First send the subscribers the current values for the events
    currentEvents.onSuccess {
      case events => events.foreach(notifySubscribers)
    }
    // Then subscribe to future events
    currentEvents.onComplete(_ => subscribe(prefixes: _*))

    def receive: Receive = {
      case event: Event => notifySubscribers(event)
    }

    private def notifySubscribers(event: Event): Unit = {
      subscriber.foreach(_ ! event)
      callback.foreach { f =>
        Future {
          f(event)
        }.onFailure {
          case ex => log.error("Event callback failed: ", ex)
        }
      }
    }
  }
}

/**
 * An implementation of the EventService trait based on Redis.
 *
 * @param host    the Redis server host
 * @param port    the Redis server port
 * @param _system Akka env required by RedisClient
 */
private[events] case class EventServiceImpl(host: String, port: Int)(implicit _system: ActorRefFactory) extends EventService {
  import EventServiceImpl._
  //  import org.slf4j.LoggerFactory
  //  import com.typesafe.scalalogging.slf4j.Logger
  //  private[events] val logger = Logger(LoggerFactory.getLogger(EventServiceImpl.getClass))

  protected val redis = RedisClient(host, port)
  implicit val execContext = _system.dispatcher

  override def publish(event: Event): Future[Unit] = publish(event, 0)

  // Publishes the event and keeps the given number of previous values
  def publish(event: Event, history: Int): Future[Unit] = {
    // Serialize the event
    val formatter = implicitly[ByteStringFormatter[Event]]
    val bs = formatter.serialize(event) // only do this once
    val h = if (history >= 0) history else 0
    // Use a transaction to send all commands at once
    val redisTransaction = redis.transaction()
    val key = event.prefix
    redisTransaction.watch(key)
    val f1 = redisTransaction.lpush(key, bs)
    val f2 = redisTransaction.ltrim(key, 0, h + 1)
    val f3 = redisTransaction.publish(key, bs)
    val f4 = redisTransaction.exec()
    Future.sequence(List(f1, f2, f3, f4)).map(_ => ())
  }

  override def subscribe(subscriber: Option[ActorRef], callback: Option[Event => Unit], prefixes: String*): EventMonitor = {
    val currentEvents = Future.sequence(prefixes.map(get)).map(_.flatten)
    EventMonitorImpl(_system.actorOf(EventMonitorActor.props(subscriber, callback, currentEvents, prefixes: _*)))
  }

  // gets the current value for the given prefix
  def get(prefix: String): Future[Option[Event]] = redis.lindex(prefix, 0)

  // Gets the last n values for the given prefix
  def getHistory(prefix: String, n: Int): Future[Seq[Event]] = redis.lrange(prefix, 0, n - 1)

  // deletes the saved value for the given prefix
  def delete(prefix: String*): Future[Long] = redis.del(prefix: _*)

  override def disconnect(): Future[Unit] = redis.quit().map(_ => ())

  override def shutdown(): Future[Unit] = {
    val f = redis.shutdown().map(_ => ())
    redis.stop()
    f.recover {
      case ex =>
        //        logger.warn("Error shutting down Redis", ex)
        ()
    }
  }
}

