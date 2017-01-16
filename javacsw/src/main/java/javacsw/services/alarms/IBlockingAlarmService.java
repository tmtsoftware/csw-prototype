package javacsw.services.alarms;

import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.services.alarms.*;
import csw.services.alarms.AlarmModel.*;

/**
 * Defines a synchronous/blocking Java API to the Alarm Service
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "SameParameterValue"})
public interface IBlockingAlarmService {

  /**
   * Sets and publishes the severity level for the given alarm
   * (severity is not refreshed).
   *
   * @param alarmKey the key for the alarm
   * @param severity the new value of the severity
   */
  void setSeverity(AlarmKey alarmKey, SeverityLevel severity);


  // --- Static factory methods to create an IBlockingAlarmService instance --

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an IBlockingAlarmService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName      name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param system      the Akka system or context, needed for working with futures and actors
   * @param timeout     amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  static IBlockingAlarmService getAlarmService(String asName, ActorSystem system, Timeout timeout) {
    return JBlockingAlarmService.lookup(asName, system, timeout);
  }

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an IBlockingAlarmService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param system  the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  static IBlockingAlarmService getAlarmService(ActorSystem system, Timeout timeout) {
    return JBlockingAlarmService.lookup(defaultName, system, timeout);
  }

  /**
   * The default name that the Alarm Service is registered with
   */
  String defaultName = AlarmService$.MODULE$.defaultName();

  /**
   * An alarm's severity should be refreshed every defaultRefreshSecs seconds
   * to make sure it does not expire and become "Disconnected" (after maxMissedRefresh missed refreshes)
   */
  int defaultRefreshSecs = AlarmService$.MODULE$.defaultRefreshSecs();

  /**
   * The default number of refreshes that may be missed before an alarm's severity is expired
   * and becomes "Disconnected"
   */
  int maxMissedRefresh = AlarmService$.MODULE$.maxMissedRefresh();
}

