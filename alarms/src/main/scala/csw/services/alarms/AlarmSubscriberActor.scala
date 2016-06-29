package csw.services.alarms

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import csw.services.alarms.AlarmModel.{AlarmStatus, SeverityLevel}
import redis.{ByteStringDeserializer, ByteStringSerializerLowPriority}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

import scala.util.{Failure, Success}

/**
 * An actor that subscribes to changes in the severity of one or more alarms and optionally
 * send an AlarmStatus message to an actor or calls a method to notifiy of a change.
 */
object AlarmSubscriberActor {
  /**
   * Subscribes to changes in alarm's severity level
   *
   * @param alarmService used to republish expired keys and lookup alarms from key names
   * @param keys        list of alarm keys to subscribe to
   */
  def props(alarmService: AlarmService, keys: List[String], subscriber: Option[ActorRef] = None, notify: Option[AlarmStatus ⇒ Unit] = None): Props =
    Props(classOf[AlarmSubscriberActor], alarmService.asInstanceOf[AlarmServiceImpl], keys, subscriber, notify)
}

private class AlarmSubscriberActor(alarmService: AlarmServiceImpl, keys: List[String], subscriber: Option[ActorRef], notify: Option[AlarmStatus ⇒ Unit])
    extends RedisSubscriberActor(
      address = new InetSocketAddress(alarmService.redisClient.host, alarmService.redisClient.port),
      channels = Seq.empty,
      patterns = "__key*__:*" :: keys,
      authPassword = None,
      onConnectStatus = (b: Boolean) ⇒ {}
    )
    with ByteStringSerializerLowPriority {

  import context.dispatcher

  override def onMessage(m: Message) = {
    log.error(s"Unexpected call to onMessage with message: $m")
  }

  override def onPMessage(pm: PMessage) = {
    val formatter = implicitly[ByteStringDeserializer[String]]
    val data = formatter.deserialize(pm.data)

    if (pm.channel.startsWith("__keyevent@0__:expired")) {
      // Key expired, data is the severity key name, publish value as Indeterminate (default when key is not defined)
      alarmService.redisClient.publish(data, SeverityLevel.Indeterminate.toString)
    } else if (pm.channel.startsWith(AlarmService.severityKeyPrefix)) {
      // An alarm's severity was published
      val key = AlarmService.makeKey(pm.channel)
      val sev = SeverityLevel(data).getOrElse(SeverityLevel.Indeterminate)
      alarmService.getAlarm(key).onComplete {
        case Success(alarmModel) ⇒
          val status = AlarmStatus(alarmModel, sev)
          subscriber.foreach(_ ! status)
          notify.foreach(_(status))
        case Failure(ex) ⇒ log.error(ex, s"Failed to get alarm for key $key")
      }
    }
  }
}
