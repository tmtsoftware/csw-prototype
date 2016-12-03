package javacsw.services.alarms;

import akka.actor.ActorRefFactory;
import akka.util.Timeout;
import csw.services.alarms.*;
import csw.services.alarms.AlarmModel.*;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import scala.Unit;

import java.util.concurrent.CompletableFuture;

/**
 * Defines the Java API to the Alarm Service
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "SameParameterValue"})
public interface IAlarmService {
  /**
   * Sets and publishes the severity level for the given alarm
   *
   * @param alarmKey the key for the alarm
   * @param severity the new value of the severity
   * @return a future indicating when the operation has completed
   */
  CompletableFuture<Unit> setSeverity(AlarmKey alarmKey, SeverityLevel severity);


  // --- Static factory methods to create an IAlarmService instance --

  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an IAlarmService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param asName      name used to register the Redis instance with the Location Service (default: "Alarm Service")
   * @param system      the Akka system or context, needed for working with futures and actors
   * @param timeout     amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  static CompletableFuture<IAlarmService> getAlarmService(String asName, ActorRefFactory system, Timeout timeout) {
    return JAlarmService.lookup(asName, system, timeout);
  }

  /**
   * Returns an IAlarmService instance using the Redis instance at the given host and port
   *
   * @param host the Redis host name or IP address
   * @param port the Redis port
   * @return a new IAlarmService instance
   */
  static IAlarmService getAlarmService(String host, int port, ActorRefFactory sys) {
    return new JAlarmService(host, port, sys);
  }


  /**
   * Looks up the Redis instance for the Alarm Service with the Location Service
   * and then returns an IAlarmService instance using it.
   * <p>
   * Note: Applications using the Location Service should call LocationService.initialize() once before
   * accessing any Akka or Location Service methods.
   *
   * @param system  the Akka system or context, needed for working with futures and actors
   * @param timeout amount of time to wait when looking up the alarm service with the location service
   * @return a new JAlarmService instance
   */
  static CompletableFuture<IAlarmService> getAlarmService(ActorRefFactory system, Timeout timeout) {
    return JAlarmService.lookup(defaultName, system, timeout);
  }

  /**
   * The default name that the Alarm Service is registered with
   */
  String defaultName = AlarmService$.MODULE$.defaultName();

  /**
   * Returns the AlarmService ComponentId for the given, or default name
   */
  static ComponentId alarmServiceComponentId(String name) {
    return AlarmService$.MODULE$.alarmServiceComponentId(name);
  }

  /**
   * Returns the AlarmService connection for the given, or default name
   */
  static Connection.TcpConnection alarmServiceConnection() {
    return AlarmService$.MODULE$.alarmServiceConnection(IAlarmService.defaultName);
  }

  /**
   * Returns the AlarmService connection for the given, or default name
   */
  static Connection.TcpConnection alarmServiceConnection(String name) {
    return AlarmService$.MODULE$.alarmServiceConnection(name);
  }

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

