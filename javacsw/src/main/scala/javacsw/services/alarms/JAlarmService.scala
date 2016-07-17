package javacsw.services.alarms

import java.io.File
import java.util
import java.util.Optional
import java.util.concurrent.CompletableFuture
import javacsw.services.alarms.IAlarmService.{AlarmHandler, HealthHandler}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import csw.services.alarms.AlarmModel.{AlarmStatus, AlarmType, CurrentSeverity, Health, HealthStatus, SeverityLevel}
import csw.services.alarms.AlarmService.AlarmMonitor
import csw.services.alarms.{AlarmKey, AlarmModel, AlarmService, AlarmState}
import csw.services.alarms.AlarmState.{AcknowledgedState, ActivationState, LatchedState, ShelvedState}
import csw.services.alarms.AscfValidation.Problem

import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._

/**
 * Support for Java API for the Alarm Service
 */
private[alarms] object JAlarmService {
  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param system the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  def lookup(asName: String, system: ActorRefFactory, timeout: Timeout): CompletableFuture[IAlarmService] = {
    import system.dispatcher
    implicit val sys = system
    implicit val t = timeout
    AlarmService(asName).map(JAlarmService(_, sys).asInstanceOf[IAlarmService]).toJava.toCompletableFuture
  }

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an AlarmService instance using it.
   *
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param refreshSecs alarm severity should be reset every refreshSecs seconds to avoid being expired and set
   *                    to "Disconnected" (after three missed refreshes)
   * @param system the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  def lookup(asName: String, refreshSecs: Int, system: ActorRefFactory, timeout: Timeout): CompletableFuture[IAlarmService] = {
    import system.dispatcher
    implicit val sys = system
    implicit val t = timeout
    AlarmService(asName, refreshSecs).map(JAlarmService(_, sys).asInstanceOf[IAlarmService]).toJava.toCompletableFuture
  }

  /**
   * Supports Java API for creating AlarmKey instances
   */
  case object JAlarmKeySup {
    /**
     * Creates an alarm key from the given optional subsystem, component and name, using wildcards in place of None.
     * The key may match multiple alarms if any of the arguments are empty or contain Redis wildcards.
     *
     * @param subsystemOpt optional subsystem (default: any)
     * @param componentOpt optional component (default: any)
     * @param nameOpt      optional alarm name (default: any)
     * @return the alarm key
     */
    def create(subsystemOpt: Optional[String], componentOpt: Optional[String], nameOpt: Optional[String]): AlarmKey =
      AlarmKey(subsystemOpt.asScala, componentOpt.asScala, nameOpt.asScala)
  }

  /**
   * Supports Java API for SeverityLevel
   */
  case object JSeverityLevelSup {
    val Disconnected = SeverityLevel.Disconnected
    val Indeterminate = SeverityLevel.Indeterminate
    val Okay = SeverityLevel.Okay
    val Warning = SeverityLevel.Warning
    val Major = SeverityLevel.Major
    val Critical = SeverityLevel.Critical
  }

  /**
   * Supports Java API for ShelvedState
   */
  case object JShelvedStateSup {
    val Shelved = ShelvedState.Shelved
    val Normal = ShelvedState.Normal
  }

  /**
   * Supports Java API for ActivationState
   */
  case object JActivationStateSup {
    val OutOfService = ActivationState.OutOfService
    val Normal = ActivationState.Normal
  }

  /**
   * Supports Java API for AcknowledgedState
   */
  case object JAcknowledgedStateSup {
    val NeedsAcknowledge = AcknowledgedState.NeedsAcknowledge
    val Normal = AcknowledgedState.Normal
  }

  /**
   * Supports Java API for LatchedState
   */
  case object JLatchedStateSup {
    val NeedsReset = LatchedState.NeedsReset
    val Normal = LatchedState.Normal
  }

  /**
   * Supports Java API for AlarmType
   */
  case object JAlarmTypeSup {
    val Absolute = AlarmType.Absolute
    val BitPattern = AlarmType.BitPattern
    val Calculated = AlarmType.Calculated
    val Deviation = AlarmType.Deviation
    val Discrepancy = AlarmType.Discrepancy
    val Instrument = AlarmType.Instrument
    val RateChange = AlarmType.RateChange
    val RecipeDriven = AlarmType.RecipeDriven
    val Safety = AlarmType.Safety
    val Statistical = AlarmType.Statistical
    val System = AlarmType.System
  }

  /**
   * Supports Java API for Health
   */
  case object JHealthSup {
    val Good = Health.Good
    val Ill = Health.Ill
    val Bad = Health.Bad
  }

}

/**
 * Java API Implementation for the Alarm Service
 */
case class JAlarmService(alarmService: AlarmService, system: ActorRefFactory) extends IAlarmService {
  import system.dispatcher

  override def refreshSecs(): Int = alarmService.refreshSecs

  override def initAlarms(inputFile: File, reset: Boolean): CompletableFuture[util.List[Problem]] =
    alarmService.initAlarms(inputFile, reset).map(_.asJava).toJava.toCompletableFuture

  override def getAlarms(alarmKey: AlarmKey): CompletableFuture[util.List[AlarmModel]] =
    alarmService.getAlarms(alarmKey).map(_.asJava).toJava.toCompletableFuture

  override def getAlarm(key: AlarmKey): CompletableFuture[AlarmModel] =
    alarmService.getAlarm(key).toJava.toCompletableFuture

  override def getAlarmState(key: AlarmKey): CompletableFuture[AlarmState] =
    alarmService.getAlarmState(key).toJava.toCompletableFuture

  override def setSeverity(alarmKey: AlarmKey, severity: SeverityLevel): CompletableFuture[Unit] =
    alarmService.setSeverity(alarmKey, severity).toJava.toCompletableFuture

  override def getSeverity(alarmKey: AlarmKey): CompletableFuture[CurrentSeverity] =
    alarmService.getSeverity(alarmKey).toJava.toCompletableFuture

  override def acknowledgeAlarm(alarmKey: AlarmKey): CompletableFuture[Unit] =
    alarmService.acknowledgeAlarm(alarmKey).toJava.toCompletableFuture

  override def setShelvedState(alarmKey: AlarmKey, shelvedState: ShelvedState): CompletableFuture[Unit] =
    alarmService.setShelvedState(alarmKey, shelvedState).toJava.toCompletableFuture

  override def setActivationState(alarmKey: AlarmKey, activationState: ActivationState): CompletableFuture[Unit] =
    alarmService.setActivationState(alarmKey, activationState).toJava.toCompletableFuture

  override def getHealth(alarmKey: AlarmKey): CompletableFuture[Health] =
    alarmService.getHealth(alarmKey).toJava.toCompletableFuture

  override def monitorHealth(alarmKey: AlarmKey, subscriber: Optional[ActorRef],
                             notifyAlarm:  Optional[AlarmHandler],
                             notifyHealth: Optional[HealthHandler]): AlarmMonitor = {
    alarmService.monitorHealth(alarmKey, subscriber.asScala,
      notifyAlarm.asScala.map(f => (alarmStatus: AlarmStatus) => f.handleAlarmStatus(alarmStatus)),
      notifyHealth.asScala.map(f => (healthStatus: HealthStatus) => f.handleHealthStatus(healthStatus)))
  }

  override def shutdown(): Unit = alarmService.shutdown()
}

/**
 * Java API to Problem companion object
 */
case object JProblem {
  /**
   * Returns the number of problems in the list with a severity or ERROR or FATAL
   */
  def errorCount(problems: java.util.List[Problem]): Int = {
    Problem.errorCount(problems.asScala.toList)
  }

  /**
   * Prints the list of problems to stdout
   */
  def printProblems(problems: java.util.List[Problem]): java.util.List[Problem] = {
    new util.ArrayList(Problem.printProblems(problems.asScala.toList).asJavaCollection)
  }
}

