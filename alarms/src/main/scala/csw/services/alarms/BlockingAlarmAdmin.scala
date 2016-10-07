package csw.services.alarms

import java.io._

import akka.actor.ActorRefFactory
import akka.util.Timeout
import csw.services.alarms.AscfValidation.Problem

import scala.concurrent.Await

/**
 * A Blocking API to the alarm service admin methods
 */
object BlockingAlarmAdmin {

  def apply(alarmService: BlockingAlarmService)(implicit system: ActorRefFactory, timeout: Timeout): BlockingAlarmAdmin =
    BlockingAlarmAdmin(AlarmServiceAdmin(alarmService.alarmService))

  def apply(alarmService: AlarmService)(implicit system: ActorRefFactory, timeout: Timeout): BlockingAlarmAdmin =
    BlockingAlarmAdmin(AlarmServiceAdmin(alarmService))
}

/**
 * A Blocking API to the alarm service admin methods
 */
case class BlockingAlarmAdmin(alarmAdmin: AlarmServiceAdmin)(implicit system: ActorRefFactory, timeout: Timeout) {

  /**
   * Initialize the alarm data in the database using the given file
   *
   * @param inputFile the alarm service config file containing info about all the alarms
   * @param reset     if true, delete the current alarms before importing (default: false)
   * @return a list of problems that occurred while validating the config file or ingesting the data into the database
   */
  def initAlarms(inputFile: File, reset: Boolean = false): List[Problem] =
    Await.result(alarmAdmin.initAlarms(inputFile, reset), timeout.duration)

  /**
   * Shuts down the the database server (For use in test cases that started the database themselves)
   */
  //  def shutdown(): Unit = Await.result(alarmAdmin.shutdown(), timeout.duration)
  def shutdown(): Unit = alarmAdmin.shutdown()
}
