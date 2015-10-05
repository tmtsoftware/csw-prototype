package csw.services.kvs

import akka.actor.ActorSystem
import redis.{ ByteStringFormatter, RedisClient }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import csw.services.kvs.KeyValueStore._

/**
 * Support for accessing a Redis server.
 * The host and port can be configured in resources/reference.conf.
 *
 * @param system the Akka actor system, needed to access the settings and RedisClient
 */
case class RedisKeyValueStore[T: ByteStringFormatter](implicit system: ActorSystem) extends KeyValueStore[T] {

  private val settings = KvsSettings(system)
  private val redis = RedisClient(settings.redisHostname, settings.redisPort)
  implicit val execContext = system.dispatcher

  override def set(key: String, value: T, expire: Option[FiniteDuration],
                   setCond: SetCondition): Future[Boolean] = {
    val msOpt = if (expire.isDefined) Some(expire.get.toMillis) else None
    val (nx, xx) = setCond match {
      case SetOnlyIfNotExists ⇒ (true, false)
      case SetOnlyIfExists    ⇒ (false, true)
      case SetAlways          ⇒ (false, false)
    }
    redis.set(key, value, None, msOpt, nx, xx)
  }

  override def get(key: String): Future[Option[T]] = {
    redis.get(key)
  }

  override def lset(key: String, value: T, history: Int): Future[Boolean] = {
    if (history >= 0) {
      // Use a transaction to send all commands at once
      val redisTransaction = redis.transaction()
      redisTransaction.watch(key)
      redisTransaction.lpush(key, value)
      redisTransaction.ltrim(key, 0, history + 1)
      val f = redisTransaction.exec()
      f.map(_.responses.isDefined) // XXX How to check if transaction was successful?
    } else {
      Future.successful(false)
    }
  }

  override def lget(key: String): Future[Option[T]] = {
    redis.lindex(key, 0)
  }

  override def getHistory(key: String, n: Int): Future[Seq[T]] = {
    redis.lrange(key, 0, n - 1)
  }

  override def delete(keys: String*): Future[Long] = {
    redis.del(keys: _*)
  }
}

