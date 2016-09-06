package javacsw.services.pkg;

import csw.services.pkg.Supervisor3.*;

/**
 * Java API for some of the static defs in the Supervisor3 Scala class
 */
public class JSupervisor3 {

  // --- Messages sent to components to notify of lifecycle changes ---

  // Someone has requested that the component shutdown
  public static final ToComponentLifecycleMessage DoShutdown =  DoShutdown$.MODULE$;

  // Someone has requested that the component restart by going back to uninitialized
  public static final ToComponentLifecycleMessage DoRestart = DoRestart$.MODULE$;

  // Supervisor reports that component is in Running and Online
  public static final ToComponentLifecycleMessage Running = Running$.MODULE$;

  // Supervisor reports that compoentn is Running but is Offline
  public static final ToComponentLifecycleMessage RunningOffline = RunningOffline$.MODULE$;

//  // Report to component that a lifecycle failure has occurred for logging, etc.
//  static class LifecycleFailureInfo(state: LifecycleState, reason: String) extends ToComponentLifecycleMessage



  // --- Messages from component indicating events ---

  // Component indicates it has Initialized successfully
  public static final FromComponentLifecycleMessage Initialized =  Initialized$.MODULE$;

//  /**
//   * Component indicates it failed to initialize with the given reason
//   *
//   * @param reason the reason for failing to initialize as a String
//   */
//  case class InitializeFailure(reason: String) extends FromComponentLifecycleMessage

  /**
   * Component indicates it has started successfully
   */
  public static final FromComponentLifecycleMessage Started = Started$.MODULE$;

//  /**
//   * Component indicates it failed to startup with the given reason
//   *
//   * @param reason reason for failing to startup as a String
//   */
//  case class StartupFailure(reason: String) extends FromComponentLifecycleMessage

  /**
   * Component indicates it has completed shutting down successfully
   */
  public static final FromComponentLifecycleMessage ShutdownComplete = ShutdownComplete$.MODULE$;

//  /**
//   * Component indicates it has failed to shutdown properly with the given reason
//   *
//   * @param reason reason for failing to shutdown as a String
//   */
//  case class ShutdownFailure(reason: String) extends FromComponentLifecycleMessage

  /**
   * Diagnostic message to shutdown and then exit supervisor/component
   */
  public static final FromComponentLifecycleMessage HaltComponent = HaltComponent$.MODULE$;

}
