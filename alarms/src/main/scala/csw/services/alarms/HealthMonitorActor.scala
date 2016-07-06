package csw.services.alarms

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.alarms.AlarmModel.{Health, HealthStatus}
import redis.{ByteStringDeserializer, ByteStringSerializerLowPriority}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

import scala.util.{Failure, Success}
import HealthMonitorActor._
import HealthMonitorWorkorActor._
import csw.services.alarms.AlarmService.HealthInfo

/**
 * An actor that monitors selected subsystem or component health and notifies a method or
 * another actor of changes in the health status.
 */
object HealthMonitorActor {
  private val keyEventPrefix = "__keyevent@0__:"

  /**
   * Used to create the actor.
   *
   * @param alarmService used to get an alarm's severity and state
   * @param alarmKey     key matching all the alarms for a subsystem or component, etc.
   * @param subscriber   an optional actor that will receive HealthStatus messages
   * @param notify       an optional function that will be called with HealthStatus messages
   */
  def props(
    alarmService: AlarmService,
    alarmKey:     AlarmKey,
    subscriber:   Option[ActorRef]            = None,
    notify:       Option[HealthStatus ⇒ Unit] = None
  ): Props = {
    Props(classOf[HealthMonitorActor], alarmService.asInstanceOf[AlarmServiceImpl], alarmKey, subscriber, notify)
  }
}

// Subscribes to changes in alarm severity for alarms matching the key and notifies the listeners on
// changes in health
private class HealthMonitorActor(
  alarmService: AlarmServiceImpl,
  alarmKey:     AlarmKey,
  subscriber:   Option[ActorRef],
  notify:       Option[HealthStatus ⇒ Unit]
)
    extends RedisSubscriberActor(
      address = new InetSocketAddress(alarmService.redisClient.host, alarmService.redisClient.port),
      channels = Seq.empty,
      patterns = List("__key*__:*", alarmKey.severityKey),
      authPassword = None,
      onConnectStatus = (b: Boolean) ⇒ {}
    )
    with ByteStringSerializerLowPriority {

  import context.dispatcher

  // Use a worker actor to keep track of the alarm severities and states, calculate health and notify listeners
  var worker = context.actorOf(HealthMonitorWorkorActor.props(alarmService, alarmKey, subscriber, notify))

  // Initialize the complete map once at startup, and then keep it up to date by subscribing to the Redis key pattern
  initAlarmMap()

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
        worker ! HealthInfo(key, sev, state)
      }
      f.onFailure { case t ⇒ log.error(t, s"Failed to get severity for key: $key") }
    }
  }

  // Update the alarmMap at startup to avoid any false alarms while waiting for alarms to come in.
  // This gets the complete list of keys (Note that static keys always exist, but severity keys may expire).
  def initAlarmMap(): Unit = {
    alarmService.getHealthInfoMap(alarmKey).onComplete {
      case Success(m)  ⇒ worker ! InitialMap(m)
      case Failure(ex) ⇒ log.error("Failed to initialize health from alarm severity and state", ex)
    }
  }
}

// Used to create the worker actor
private object HealthMonitorWorkorActor {

  // Initial map with alarm data
  case class InitialMap(alarmMap: Map[AlarmKey, HealthInfo])

  /**
   * Used to create the actor.
   *
   * @param alarmService reference to alarm service instance
   * @param alarmKey     key matching all the alarms for a subsystem or component, etc.
   * @param subscriber   an optional actor that will receive HealthStatus messages
   * @param notify       an optional function that will be called with HealthStatus messages
   */
  def props(
    alarmService: AlarmServiceImpl,
    alarmKey:     AlarmKey,
    subscriber:   Option[ActorRef]            = None,
    notify:       Option[HealthStatus ⇒ Unit] = None
  ): Props = {
    Props(classOf[HealthMonitorWorkorActor], alarmService, alarmKey, subscriber, notify)
  }
}

// Manages a map of alarm severity and state, calculates health when something changes
// and notifies the listeners when the health value changes
private class HealthMonitorWorkorActor(
  alarmService:    AlarmServiceImpl,
  alarmKeyPattern: AlarmKey,
  subscriber:      Option[ActorRef],
  notify:          Option[HealthStatus ⇒ Unit]
) extends Actor with ActorLogging {

  var healthOpt: Option[Health] = None

  // Expect an initial map containing all the alarms we are tracking
  override def receive: Receive = {
    case InitialMap(alarmMap) ⇒
      context.become(working(alarmMap))
      updateHealth(alarmMap)

    case x ⇒ log.debug(s"Ignoring message since not ready: $x")
  }

  // expect updates to the alarm severity or state, then calculate the new health value
  private def working(alarmMap: Map[AlarmKey, HealthInfo]): Receive = {
    case h @ HealthInfo(alarmKey, severityLevel, alarmState) ⇒
      val healthInfo = alarmMap(alarmKey)
      if (healthInfo.severityLevel != h.severityLevel || healthInfo.alarmState != h.alarmState) {
        context.become(working(alarmMap + (alarmKey → h)))
        updateHealth(alarmMap)
      }
  }

  // Calculate the health from the alarm data and notify the listeners if something changed
  private def updateHealth(alarmMap: Map[AlarmKey, HealthInfo]): Unit = {
    val health = alarmService.getHealth(alarmMap)

    // Notify listeners if the health changed
    if (healthOpt.isEmpty || healthOpt.get != health) {
      healthOpt = Some(health)
      notifyListeners(HealthStatus(alarmKeyPattern, health))
    }
  }

  // Notify the subscribers of a change in the severity of the alarm
  private def notifyListeners(healthStatus: HealthStatus): Unit = {
    subscriber.foreach(_ ! healthStatus)
    notify.foreach(_(healthStatus))
  }
}

