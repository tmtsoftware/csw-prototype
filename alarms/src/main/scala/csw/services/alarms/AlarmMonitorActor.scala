package csw.services.alarms

import java.net.InetSocketAddress
import java.time.{Clock, LocalDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.alarms.AlarmModel.{AlarmStatus, Health, HealthStatus}
import redis.{ByteStringDeserializer, ByteStringSerializerLowPriority}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

import scala.util.{Failure, Success}
import AlarmMonitorActor._
import AlarmMonitorWorkorActor._
import csw.services.alarms.AlarmService.HealthInfo
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}

import scala.concurrent.Future

/**
 * An actor that monitors selected subsystem or component health and notifies a method or
 * another actor of changes in the health status.
 */
object AlarmMonitorActor {
  private val keyEventPrefix = "__keyevent@0__:"

  /**
   * Used to create the actor.
   *
   * @param alarmService used to get an alarm's severity and state
   * @param alarmKey     key matching all the alarms for a subsystem or component, etc.
   * @param subscriber   an optional actor that will receive HealthStatus and AlarmStatus messages
   * @param notifyHealth an optional function that will be called with HealthStatus messages
   * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
   *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                     and where the latched severity or calculated health actually changed
   */
  def props(
    alarmService: AlarmService,
    alarmKey:     AlarmKey,
    subscriber:   Option[ActorRef]             = None,
    notifyAlarm:  Option[AlarmStatus => Unit]  = None,
    notifyHealth: Option[HealthStatus => Unit] = None,
    notifyAll:    Boolean                      = false
  ): Props = {
    Props(classOf[AlarmMonitorActor], alarmService.asInstanceOf[AlarmServiceImpl], alarmKey, subscriber,
      notifyAlarm, notifyHealth, notifyAll)
  }
}

// Subscribes to changes in alarm severity for alarms matching the key and notifies the listeners on
// changes in health
private class AlarmMonitorActor(
  alarmService: AlarmServiceImpl,
  alarmKey:     AlarmKey,
  subscriber:   Option[ActorRef],
  notifyAlarm:  Option[AlarmStatus => Unit],
  notifyHealth: Option[HealthStatus => Unit],
  notifyAll:    Boolean
) extends RedisSubscriberActor(
  address = new InetSocketAddress(alarmService.redisClient.host, alarmService.redisClient.port),
  channels = Seq.empty,
  patterns = List("__key*__:*", alarmKey.severityKey),
  authPassword = None,
  onConnectStatus = (b: Boolean) => {}
)
    with ByteStringSerializerLowPriority {

  import context.dispatcher

  // Use a worker actor to keep track of the alarm severities and states, calculate health and notify listeners
  var worker = context.actorOf(AlarmMonitorWorkorActor.props(alarmService, alarmKey, subscriber, notifyAlarm, notifyHealth, notifyAll))

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
        sev <- alarmService.getSeverity(key)
        state <- alarmService.getAlarmState(key)
      } yield {
        worker ! HealthInfo(key, sev, state)
      }
      f.onFailure { case t => log.error(t, s"Failed to get severity for key: $key") }
    }
  }

  // Update the alarmMap at startup to avoid any false alarms while waiting for alarms to come in.
  // This gets the complete list of keys (Note that static keys always exist, but severity keys may expire).
  def initAlarmMap(): Unit = {
    alarmService.getHealthInfoMap(alarmKey).onComplete {
      case Success(m)  => worker ! InitialMap(m)
      case Failure(ex) => log.error("Failed to initialize health from alarm severity and state", ex)
    }
  }
}

// Used to create the worker actor
private object AlarmMonitorWorkorActor {

  // Initial map with alarm data
  case class InitialMap(alarmMap: Map[AlarmKey, HealthInfo])

  /**
   * Used to create the actor.
   *
   * @param alarmService reference to alarm service instance
   * @param alarmKey     key matching all the alarms for a subsystem or component, etc.
   * @param subscriber   an optional actor that will receive HealthStatus messages
   * @param notifyHealth an optional function that will be called with HealthStatus messages
   * @param notifyAll    if true, all severity changes are reported (for example, for logging), otherwise
   *                     only the relevant changes in alarms are reported, for alarms that are not shelved and not out of service,
   *                     and where the latched severity or calculated health actually changed
   */
  def props(
    alarmService: AlarmServiceImpl,
    alarmKey:     AlarmKey,
    subscriber:   Option[ActorRef],
    notifyAlarm:  Option[AlarmStatus => Unit],
    notifyHealth: Option[HealthStatus => Unit],
    notifyAll:    Boolean
  ): Props = {
    Props(classOf[AlarmMonitorWorkorActor], alarmService, alarmKey, subscriber, notifyAlarm, notifyHealth, notifyAll)
  }
}

// Manages a map of alarm severity and state, calculates health when something changes
// and notifies the listeners when the health value changes
private class AlarmMonitorWorkorActor(
    alarmService:    AlarmServiceImpl,
    alarmKeyPattern: AlarmKey,
    subscriber:      Option[ActorRef],
    notifyAlarm:     Option[AlarmStatus => Unit],
    notifyHealth:    Option[HealthStatus => Unit],
    notifyAll:       Boolean
) extends Actor with ActorLogging {

  import context.dispatcher

  var healthOpt: Option[Health] = None

  // Expect an initial map containing all the alarms we are tracking
  override def receive: Receive = {
    case InitialMap(alarmMap) =>
      context.become(working(alarmMap))
      updateHealth(alarmMap)

    case x => log.debug(s"Ignoring message since not ready: $x")
  }

  // Returns a timestamp for an alarm
  private def timestamp(): LocalDateTime = LocalDateTime.now(Clock.systemUTC())

  // expect updates to the alarm severity or state, then calculate the new health value
  private def working(alarmMap: Map[AlarmKey, HealthInfo]): Receive = {
    case h @ HealthInfo(alarmKey, severityLevel, alarmState) =>
      val update = if (alarmMap.contains(alarmKey)) {
        val healthInfo = alarmMap(alarmKey)
        healthInfo.currentSeverity != h.currentSeverity || healthInfo.alarmState != h.alarmState
      } else false
      if (update) {
        val newMap = alarmMap + (alarmKey -> h)
        context.become(working(newMap))
        updateHealth(newMap)
        if (notifyAll || (alarmState.shelvedState == ShelvedState.Normal && alarmState.activationState == ActivationState.Normal))
          notifyListeners(AlarmStatus(timestamp(), alarmKey, severityLevel, alarmState))
      }
  }

  // Calculate the health from the alarm data and notify the listeners if something changed
  private def updateHealth(alarmMap: Map[AlarmKey, HealthInfo]): Unit = {
    val health = alarmService.getHealth(alarmMap)

    // Notify listeners if the health changed
    if (healthOpt.isEmpty || healthOpt.get != health) {
      healthOpt = Some(health)
      notifyListeners(HealthStatus(timestamp(), alarmKeyPattern, health))
    }
  }

  // Notify the subscribers of a change in the health
  private def notifyListeners(healthStatus: HealthStatus): Unit = {
    subscriber.foreach(_ ! healthStatus)

    // Run callback in a future and report any errors
    notifyHealth.foreach { f =>
      Future {
        f(healthStatus)
      }.onFailure {
        case ex => log.error("Health notification callback failed: ", ex)
      }
    }
  }

  // Notify the subscribers of a change in the severity of an alarm
  private def notifyListeners(alarmStatus: AlarmStatus): Unit = {
    subscriber.foreach(_ ! alarmStatus)

    // Run callback in a future and report any errors
    notifyAlarm.foreach { f =>
      Future {
        f(alarmStatus)
      }.onFailure {
        case ex => log.error("Alarm notification callback failed: ", ex)
      }
    }
  }
}

