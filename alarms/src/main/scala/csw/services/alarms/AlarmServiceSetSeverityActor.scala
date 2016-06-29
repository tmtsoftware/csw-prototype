package csw.services.alarms

import akka.actor.{Actor, ActorLogging}
import redis.RedisClient

/**
 * An actor that continuously sets the severity of an alarm to a given value,
 * to keep the value from expiring in the database
 */
object AlarmServiceSetSeverityActor {

}

private class AlarmServiceSetSeverityActor(redisClient: RedisClient) extends Actor with ActorLogging {

  def receive: Receive = {
    case x ⇒ log.error(s"Received unexpected message: $x")
  }

}
