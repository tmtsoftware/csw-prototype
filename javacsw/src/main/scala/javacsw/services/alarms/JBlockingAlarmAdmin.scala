package javacsw.services.alarms

import java.io.File

import akka.actor.ActorRefFactory
import akka.util.Timeout
import csw.services.alarms.{BlockingAlarmAdmin, BlockingAlarmService}
import csw.services.alarms.AscfValidation.Problem

import scala.collection.JavaConverters._

/**
 * Implements a blocking java admin API for the Alarm Service
 */
case class JBlockingAlarmAdmin(alarmService: IBlockingAlarmService, timeout: Timeout, system: ActorRefFactory) extends IBlockingAlarmAdmin {
  implicit val sys = system
  implicit val t = timeout

  val alarmAdmin = BlockingAlarmAdmin(alarmService.asInstanceOf[JBlockingAlarmService].alarmService)

  override def initAlarms(inputFile: File, reset: Boolean): java.util.List[Problem] =
    alarmAdmin.initAlarms(inputFile, reset).asJava

  override def shutdown(): Unit = alarmAdmin.shutdown()
}
