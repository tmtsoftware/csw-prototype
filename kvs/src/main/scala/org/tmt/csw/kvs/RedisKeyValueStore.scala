package org.tmt.csw.kvs

import akka.actor.ActorSystem
import redis.{ByteStringFormatter, RedisClient}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import org.tmt.csw.kvs.KeyValueStore._

/**
 * A Redis based key value store.
 */
object RedisKeyValueStore {

}

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

  override def set(key: String, event: Event, expire: Option[FiniteDuration] = None,
          setCond: SetCondition = SetAlways): Future[Boolean] = {
    val msOpt = if (expire.isDefined) Some(expire.get.toMillis) else None
    val (nx, xx) = setCond match {
      case SetOnlyIfNotExists => (true, false)
      case SetOnlyIfExists => (false, true)
      case SetAlways => (false, false)
    }
    redis.set(key, event, None, msOpt, nx, xx)
  }

  override def get(key: String): Future[Option[Event]] = {
    redis.get(key).map {
      case Some(byteString) => Some(formatter.deserialize(byteString))
      case None => None
    }
  }

  override def delete(keys: String*): Future[Long] = {
    redis.del(keys: _*)
  }
}

