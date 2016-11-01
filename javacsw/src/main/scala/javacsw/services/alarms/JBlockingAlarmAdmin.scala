package javacsw.services.alarms

import java.io.File

import csw.services.alarms._
import csw.services.alarms.AscfValidation.Problem
import java.util
import javacsw.services.alarms.IAlarmServiceAdmin.{AlarmHandler, HealthHandler}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import csw.services.alarms.AlarmModel.{AlarmStatus, CurrentSeverity, Health, HealthStatus}
import csw.services.alarms.AlarmService.AlarmMonitor
import csw.services.alarms.{AlarmKey, AlarmModel, AlarmState}
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}

import scala.collection.JavaConverters._

/**
 * Implements a blocking java admin API for the Alarm Service
 */
case class JBlockingAlarmAdmin(alarmService: IBlockingAlarmService, timeout: Timeout, system: ActorRefFactory) extends IBlockingAlarmAdmin {
  implicit val sys = system
  implicit val t = timeout

  val alarmAdmin = BlockingAlarmServiceAdmin(alarmService.asInstanceOf[JBlockingAlarmService].alarmService)

  override def initAlarms(inputFile: File, reset: Boolean): java.util.List[Problem] =
    alarmAdmin.initAlarms(inputFile, reset).asJava

  override def getAlarms(alarmKey: AlarmKey): util.List[AlarmModel] =
    alarmAdmin.getAlarms(alarmKey).asJava

  override def getAlarm(key: AlarmKey): AlarmModel =
    alarmAdmin.getAlarm(key)

  override def getAlarmState(key: AlarmKey): AlarmState =
    alarmAdmin.getAlarmState(key)

  override def getSeverity(alarmKey: AlarmKey): CurrentSeverity =
    alarmAdmin.getSeverity(alarmKey)

  override def acknowledgeAlarm(alarmKey: AlarmKey): Unit =
    alarmAdmin.acknowledgeAlarm(alarmKey)

  override def resetAlarm(alarmKey: AlarmKey): Unit =
    alarmAdmin.resetAlarm(alarmKey)

  override def acknowledgeAndResetAlarm(alarmKey: AlarmKey): Unit =
    alarmAdmin.acknowledgeAndResetAlarm(alarmKey)

  override def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): Unit =
    alarmAdmin.setShelvedState(alarmKey, shelvedState)

  override def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): Unit =
    alarmAdmin.setActivationState(alarmKey, activationState)

  override def getHealth(alarmKey: AlarmKey): Health =
    alarmAdmin.getHealth(alarmKey)

  override def monitorAlarms(alarmKey: AlarmKey, subscriber: ActorRef, notifyAll: Boolean): AlarmMonitor = {
    alarmAdmin.monitorAlarms(alarmKey, subscriber, notifyAll)
  }

  override def monitorAlarms(
    alarmKey:     AlarmKey,
    notifyAlarm:  AlarmHandler,
    notifyHealth: HealthHandler,
    notifyAll:    Boolean
  ): AlarmMonitor = {
    alarmAdmin.monitorAlarms(
      alarmKey,
      (alarmStatus: AlarmStatus) => notifyAlarm.handleAlarmStatus(alarmStatus),
      (healthStatus: HealthStatus) => notifyHealth.handleHealthStatus(healthStatus),
      notifyAll
    )
  }

  override def shutdown(): Unit = alarmAdmin.shutdown()
}
