package org.tmt.csw.kvs

import akka.actor.ActorSystem
import redis.RedisClient
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


  def set(key: String, value: String, expire: Option[FiniteDuration] = None,
          setCond: SetCondition = SetAlways): Future[Boolean] = {
    val msOpt = if (expire.isDefined) Some(expire.get.toMillis) else None
    val (nx, xx) = setCond match {
      case SetOnlyIfNotExists => (true, false)
      case SetOnlyIfExists => (false, true)
      case SetAlways => (false, false)
    }
    redis.set(key, value, None, msOpt, nx, xx)
  }


  def get(key: String): Future[Option[String]] = {
    implicit val execContext = system.dispatcher
    redis.get(key).map {
      case Some(byteString) => Some(byteString.utf8String)
      case None => None
    }
  }

  def delete(keys: String*): Future[Long] = {
    redis.del(keys: _*)
  }
}

