package javacsw.services.alarms

import java.io.File
import java.util
import java.util.concurrent.CompletableFuture
import javacsw.services.alarms.IAlarmServiceAdmin.{AlarmHandler, HealthHandler}

import akka.actor.{ActorRef, ActorRefFactory}
import csw.services.alarms.AlarmModel.{AlarmStatus, CurrentSeverity, Health, HealthStatus}
import csw.services.alarms.AlarmService.AlarmMonitor
import csw.services.alarms.AlarmState.{ActivationState, ShelvedState}
import csw.services.alarms.{AlarmKey, AlarmModel, AlarmServiceAdmin, AlarmState}
import csw.services.alarms.AscfValidation.Problem

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

object JAlarmServiceAdmin {
  /**
   * Starts a redis instance on a random free port (redis-server must be in your shell path)
   * and registers it with the location service.
   * This is the equivalent of running this from the command line:
   * {{{
   *   tracklocation --name "Alarm Service Test" --command "redis-server --port %port" --no-exit
   * }}}
   *
   * This method is mainly for use by tests. In production, you would use the tracklocation app
   * to start Redis once.
   *
   * @param name   The name to use to register the alarm service with the location service
   * @param noExit if true, do not exit the application when redis exists
   * @param ec     required for futures
   * @return a future that completes when the redis server exits
   */
  def startAlarmService(name: String, noExit: Boolean, ec: ExecutionContext): CompletableFuture[Unit] = {
    AlarmServiceAdmin.startAlarmService(name, noExit)(ec).toJava.toCompletableFuture
  }
}

/**
 * Implements the java admin API for Alarm Service
 */
class JAlarmServiceAdmin(alarmService: IAlarmService, system: ActorRefFactory) extends IAlarmServiceAdmin {

  import system.dispatcher

  implicit val sys = system

  val alarmAdmin = AlarmServiceAdmin(alarmService.asInstanceOf[JAlarmService].alarmService)

  override def initAlarms(inputFile: File, reset: Boolean): CompletableFuture[util.List[Problem]] =
    alarmAdmin.initAlarms(inputFile, reset).map(_.asJava).toJava.toCompletableFuture

  override def getAlarms(alarmKey: AlarmKey): CompletableFuture[util.List[AlarmModel]] =
    alarmAdmin.getAlarms(alarmKey).map(_.asJava).toJava.toCompletableFuture

  override def getAlarm(key: AlarmKey): CompletableFuture[AlarmModel] =
    alarmAdmin.getAlarm(key).toJava.toCompletableFuture

  override def getAlarmState(key: AlarmKey): CompletableFuture[AlarmState] =
    alarmAdmin.getAlarmState(key).toJava.toCompletableFuture

  override def getSeverity(alarmKey: AlarmKey): CompletableFuture[CurrentSeverity] =
    alarmAdmin.getSeverity(alarmKey).toJava.toCompletableFuture

  override def acknowledgeAlarm(alarmKey: AlarmKey): CompletableFuture[Unit] =
    alarmAdmin.acknowledgeAlarm(alarmKey).toJava.toCompletableFuture

  override def resetAlarm(alarmKey: AlarmKey): CompletableFuture[Unit] =
    alarmAdmin.resetAlarm(alarmKey).toJava.toCompletableFuture

  override def acknowledgeAndResetAlarm(alarmKey: AlarmKey): CompletableFuture[Unit] =
    alarmAdmin.acknowledgeAndResetAlarm(alarmKey).toJava.toCompletableFuture

  override def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): CompletableFuture[Unit] =
    alarmAdmin.setShelvedState(alarmKey, shelvedState).toJava.toCompletableFuture

  override def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): CompletableFuture[Unit] =
    alarmAdmin.setActivationState(alarmKey, activationState).toJava.toCompletableFuture

  override def getHealth(alarmKey: AlarmKey): CompletableFuture[Health] =
    alarmAdmin.getHealth(alarmKey).toJava.toCompletableFuture

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

  override def shutdown(): CompletableFuture[Unit] = alarmAdmin.shutdown().toJava.toCompletableFuture
}

