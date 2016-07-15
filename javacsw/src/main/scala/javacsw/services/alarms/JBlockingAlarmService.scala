package javacsw.services.alarms

import java.io.File
import java.util
import java.util.Optional

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import csw.services.alarms.AlarmModel.{AlarmStatus, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmService.AlarmMonitor
import csw.services.alarms.{AlarmKey, AlarmModel, AlarmService, AlarmState}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem
import javacsw.services.alarms.IAlarmService.{AlarmHandler, HealthHandler}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.Await

/**
 * Support for blocking Java API for the Alarm Service
 */
private[alarms] object JBlockingAlarmService {
  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName  name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param system  the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new IBlockingAlarmService instance
   */
  def lookup(asName: String, system: ActorRefFactory, timeout: Timeout): IBlockingAlarmService = {
    import system.dispatcher
    implicit val sys = system
    implicit val t = timeout
    Await.result(AlarmService(asName).map(JBlockingAlarmService(_, timeout, sys).asInstanceOf[IBlockingAlarmService]), timeout.duration)
  }

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName      name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param refreshSecs alarm severity should be reset every refreshSecs seconds to avoid being expired and set
   *                    to "Disconnected" (after three missed refreshes)
   * @param system      the Akka system or context, needed for working with futures and actors
   * @param timeout     amount of time to wait when looking up the alarm service with the location service
   * @return a new JBlockingAlarmService instance
   */
  def lookup(asName: String, refreshSecs: Int, system: ActorRefFactory, timeout: Timeout): IBlockingAlarmService = {
    import system.dispatcher
    implicit val sys = system
    implicit val t = timeout
    Await.result(AlarmService(asName, refreshSecs).map(JBlockingAlarmService(_, timeout, sys).asInstanceOf[IBlockingAlarmService]), timeout.duration)
  }
}

/**
 * Java API Implementation for the Alarm Service
 */
case class JBlockingAlarmService(alarmService: AlarmService, timeout: Timeout, system: ActorRefFactory) extends IBlockingAlarmService {

  import system.dispatcher

  override def refreshSecs(): Int = alarmService.refreshSecs

  override def initAlarms(inputFile: File, reset: Boolean): util.List[Problem] =
    Await.result(alarmService.initAlarms(inputFile, reset).map(_.asJava), timeout.duration)

  override def getAlarms(alarmKey: AlarmKey): util.List[AlarmModel] =
    Await.result(alarmService.getAlarms(alarmKey).map(_.asJava), timeout.duration)

  override def getAlarm(key: AlarmKey): AlarmModel =
    Await.result(alarmService.getAlarm(key), timeout.duration)

  override def getAlarmState(key: AlarmKey): AlarmState =
    Await.result(alarmService.getAlarmState(key), timeout.duration)

  override def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): Unit =
    Await.result(alarmService.setSeverity(alarmKey, severity), timeout.duration)

  override def getSeverity(alarmKey: AlarmKey): SeverityLevel =
    Await.result(alarmService.getSeverity(alarmKey), timeout.duration)

  override def acknowledgeAlarm(alarmKey: AlarmKey): Unit =
    Await.result(alarmService.acknowledgeAlarm(alarmKey), timeout.duration)

  override def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Unit =
    Await.result(alarmService.setShelvedState(alarmKey, shelvedState), timeout.duration)

  override def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Unit =
    Await.result(alarmService.setActivationState(alarmKey, activationState), timeout.duration)

  override def getHealth(alarmKey: AlarmKey): Health =
    Await.result(alarmService.getHealth(alarmKey), timeout.duration)

  override def monitorHealth(alarmKey: AlarmKey, subscriber: Optional[ActorRef],
                             notifyAlarm: Optional[AlarmHandler], notifyHealth: Optional[HealthHandler]): AlarmMonitor = {
    alarmService.monitorHealth(alarmKey, subscriber.asScala,
      notifyAlarm.asScala.map(f => (alarmStatus: AlarmStatus) => f.handleAlarmStatus(alarmStatus)),
      notifyHealth.asScala.map(f => (healthStatus: HealthStatus) => f.handleHealthStatus(healthStatus)))
  }

  override def shutdown(): Unit = alarmService.shutdown()
}

