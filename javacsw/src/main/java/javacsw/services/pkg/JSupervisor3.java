package javacsw.services.pkg;

import csw.services.pkg.Supervisor3.*;

/**
 * Java API for some of the static defs in the Supervisor3 Scala class
 */
@SuppressWarnings("unused")
public class JSupervisor3 {


  // The following are states used for the Supervisor lifecycle manager

  /**
   * State of the Supervisor when started and waiting for the first lifecycle message from the component.
   */
  public static final LifecycleState LifecycleWaitingForInitialized =  LifecycleWaitingForInitialized$.MODULE$;

  /**
   * State of the Supervisor when Initialized after receiving the [[csw.services.pkg.Supervisor3.Initialized]]
   * message (first) from the component
   */
  public static final LifecycleState LifecycleInitialized =  LifecycleInitialized$.MODULE$;

  /**
   * State of the Supervisor after receiving the [[csw.services.pkg.Supervisor3.Started]]
   * message (second) from the component. Component is Running and Online at this point.
   * Component receives a [[csw.services.pkg.Supervisor3.Running]] message indicating this.
   */
  public static final LifecycleState LifecycleRunning =  LifecycleRunning$.MODULE$;

  /**
   * State of the Supervisor/component after receiving an [[csw.services.pkg.SupervisorExternal.ExComponentOffline]]
   * message to place the component offline. The component receives the [[csw.services.pkg.Supervisor3.RunningOffline]]
   * message indicating this.
   */
  public static final LifecycleState LifecycleRunningOffline =  LifecycleRunningOffline$.MODULE$;

  /**
   * State of the Supervisor/component after receiving an [[csw.services.pkg.SupervisorExternal.ExComponentShutdown]]
   * message to shutdown the component. The component receives the [[csw.services.pkg.Supervisor3.DoShutdown]]
   * message indicating this.
   */
  public static final LifecycleState LifecyclePreparingToShutdown =  LifecyclePreparingToShutdown$.MODULE$;

  /**
   * State of the Supervisor/component after the component has indicated it could not initialize or startup
   * successfully.
   */
  public static final LifecycleState LifecycleFailure =  LifecycleFailure$.MODULE$;

  /**
   * State of the Supervisor/component after the component has indicated it is ready to shutdown after receiving
   * the [[csw.services.pkg.Supervisor3.ShutdownComplete]] message.
   */
  public static final LifecycleState LifecycleShutdown =  LifecycleShutdown$.MODULE$;

  /**
   * State of the Supervisor/component when the component indicated it could not get ready to shutdown or failed
   * to notify the Supervisor with the [[csw.services.pkg.Supervisor3.ShutdownComplete]] message within the
   * timeout.
   */
  public static final LifecycleState LifecycleShutdownFailure =  LifecycleShutdownFailure$.MODULE$;


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
