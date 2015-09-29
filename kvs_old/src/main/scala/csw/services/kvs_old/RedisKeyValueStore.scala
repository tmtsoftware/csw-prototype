package csw.services.kvs_old

import akka.actor.ActorSystem
import redis.{ ByteStringFormatter, RedisClient }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import csw.services.kvs_old.KeyValueStore._

/**
 * Support for accessing a Redis server.
 * The host and port can be configured in resources/reference.conf.
 *
 * @param system the Akka actor system, needed to access the settings and RedisClient
 */
case class RedisKeyValueStore(implicit system: ActorSystem) extends KeyValueStore {
  private val settings = KvsSettings(system)
  private val redis = RedisClient(settings.redisHostname, settings.redisPort)
  val formatter = implicitly[ByteStringFormatter[Event]]
  implicit val execContext = system.dispatcher

  override def set(key: String, event: Event, expire: Option[FiniteDuration],
                   setCond: SetCondition): Future[Boolean] = {
    val msOpt = if (expire.isDefined) Some(expire.get.toMillis) else None
    val (nx, xx) = setCond match {
      case SetOnlyIfNotExists ⇒ (true, false)
      case SetOnlyIfExists    ⇒ (false, true)
      case SetAlways          ⇒ (false, false)
    }
    redis.set(key, event, None, msOpt, nx, xx)
  }

  override def get(key: String): Future[Option[Event]] = {
    redis.get(key)
  }

  override def lset(key: String, event: Event, history: Int): Future[Boolean] = {
    if (history >= 0) {
      // Serialize the event
      val formatter = implicitly[ByteStringFormatter[Event]]
      val bs = formatter.serialize(event)

      // Use a transaction to send all commands at once
      val redisTransaction = redis.transaction()
      redisTransaction.watch(key)
      redisTransaction.lpush(key, bs)
      redisTransaction.ltrim(key, 0, history + 1)
      val f = redisTransaction.exec()
      f.map(_.responses.isDefined) // XXX How to check if transaction was successful?
    } else {
      Future.successful(false)
    }
  }

  override def lget(key: String): Future[Option[Event]] = {
    redis.lindex(key, 0)
  }

  override def getHistory(key: String, n: Int): Future[Seq[Event]] = {
    redis.lrange(key, 0, n - 1)
  }

  override def delete(keys: String*): Future[Long] = {
    redis.del(keys: _*)
  }
}

