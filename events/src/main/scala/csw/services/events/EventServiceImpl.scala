package csw.services.events

import akka.actor.ActorRefFactory
import akka.util.ByteString
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
  protected val redis = RedisClient(host, port)
  implicit val execContext = _system.dispatcher

  override def publish(event: Event, history: Int): Future[Unit] = {
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

  override def get(prefix: String): Future[Option[Event]] = redis.lindex(prefix, 0)

  override def getHistory(prefix: String, n: Int): Future[Seq[Event]] = redis.lrange(prefix, 0, n - 1)

  override def delete(prefix: String*): Future[Long] = redis.del(prefix: _*)

  def disconnect(): Future[Unit] = redis.quit().map(_ => ())

  def shutdown(): Future[Unit] = {
    val f = redis.shutdown().map(_ => ())
    redis.stop()
    f
  }
}

