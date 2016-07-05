package csw.services.alarms

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import csw.services.alarms.AlarmModel.{AlarmStatus, SeverityLevel}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import redis.{ByteStringDeserializer, ByteStringSerializerLowPriority}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

import scala.util.{Failure, Success}

/**
 * An actor that subscribes to changes in the severity of one or more alarms and optionally
 * send an AlarmStatus message to an actor or calls a method to notifiy of a change.
 */
object AlarmSubscriberActor {
  private val keyEventPrefix = "__keyevent@0__:"

  /**
   * Subscribes to changes in alarm's severity level
   *
   * @param alarmService used to republish expired keys and lookup alarms from key names
   * @param keys        list of alarm keys to subscribe to
   */
  def props(alarmService: AlarmService, keys: List[AlarmKey], subscriber: Option[ActorRef] = None, notify: Option[AlarmStatus ⇒ Unit] = None): Props =
    Props(classOf[AlarmSubscriberActor], alarmService.asInstanceOf[AlarmServiceImpl], keys, subscriber, notify)
}

private class AlarmSubscriberActor(alarmService: AlarmServiceImpl, keys: List[AlarmKey], subscriber: Option[ActorRef], notify: Option[AlarmStatus ⇒ Unit])
    extends RedisSubscriberActor(
      address = new InetSocketAddress(alarmService.redisClient.host, alarmService.redisClient.port),
      channels = Seq.empty,
      patterns = "__key*__:*" :: keys.map(_.severityKey),
      authPassword = None,
      onConnectStatus = (b: Boolean) ⇒ {}
    )
    with ByteStringSerializerLowPriority {

  import context.dispatcher
  import AlarmSubscriberActor._

  override def onMessage(m: Message): Unit = {
    log.error(s"Unexpected call to onMessage with message: $m")
  }

  override def onPMessage(pm: PMessage): Unit = {
    val formatter = implicitly[ByteStringDeserializer[String]]

    if (pm.channel.startsWith(s"${keyEventPrefix}expired") || pm.channel.startsWith(s"${keyEventPrefix}set")) {
      // Key was set, data is the severity key name
      val key = AlarmKey(formatter.deserialize(pm.data))
      val s = pm.channel.substring(keyEventPrefix.length)
      log.debug(s"key $s: $key")
      val f = for {
        sev ← alarmService.getSeverity(key)
        state ← alarmService.getAlarmState(key)
      } yield {
        if (state.shelvedState == ShelvedState.Normal && state.activationState == ActivationState.Normal)
          notifyListeners(key, sev, state)
      }
      f.onFailure { case t ⇒ log.error(t, s"Failed to get severity for key: $key") }
    }
  }

  // Notify the subscribers of a change in the severity of the alarm
  private def notifyListeners(key: AlarmKey, severity: SeverityLevel, state: AlarmState): Unit = {
    alarmService.getAlarm(key).onComplete {
      case Success(alarmModel) ⇒
        val status = AlarmStatus(alarmModel, severity, state)
        subscriber.foreach(_ ! status)
        notify.foreach(_(status))
      case Failure(ex) ⇒ log.error(ex, s"Failed to get alarm for key $key")
    }
  }
}
