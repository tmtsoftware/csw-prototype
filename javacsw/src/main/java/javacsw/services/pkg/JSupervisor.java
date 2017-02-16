package javacsw.services.pkg;

import akka.actor.ActorRef;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor;
import csw.services.pkg.Supervisor.*;
import csw.services.pkg.SupervisorExternal.*;

/**
 * Java API for some of the static defs in the Supervisor Scala class
 */
@SuppressWarnings("unused")
public class JSupervisor {

  /**
   * Returns a new supervisor actor managing the components described in the argument
   *
   * @param componentInfo describes the components to create and manage
   * @return the actorRef for the supervisor (parent actor of the top level component)
   */
  public static ActorRef create(Component.ComponentInfo componentInfo) {
    return Supervisor.apply(componentInfo);
  }

  // The following are states used for the Supervisor lifecycle manager

  /**
   * State of the Supervisor when started and waiting for the first lifecycle message from the component.
   */
  public static final LifecycleState LifecycleWaitingForInitialized =  LifecycleWaitingForInitialized$.MODULE$;

  /**
   * State of the Supervisor/component after the component has indicated it could not initialize successfully.
   * Component receives the [[LifecycleFailureInfo]] message and can take a failure action.
   */
  public static final LifecycleState LifecycleInitializeFailure =  LifecycleInitializeFailure$.MODULE$;

  /**
   * State of the Supervisor after receiving the [[csw.services.pkg.Supervisor.Started]]
   * message (second) from the component. Component is Running and Online at this point.
   * Component receives a [[csw.services.pkg.Supervisor.Running]] message indicating this.
   */
  public static final LifecycleState LifecycleRunning =  LifecycleRunning$.MODULE$;

  /**
   * State of the Supervisor/component after receiving an [[csw.services.pkg.SupervisorExternal.ExComponentOffline]]
   * message to place the component offline. The component receives the [[csw.services.pkg.Supervisor.RunningOffline]]
   * message indicating this.
   */
  public static final LifecycleState LifecycleRunningOffline =  LifecycleRunningOffline$.MODULE$;

  /**
   * State of the Supervisor/component after receiving an [[csw.services.pkg.SupervisorExternal.ExComponentShutdown]]
   * message to shutdown the component. The component receives the [[csw.services.pkg.Supervisor.DoShutdown]]
   * message indicating this.
   */
  public static final LifecycleState LifecyclePreparingToShutdown =  LifecyclePreparingToShutdown$.MODULE$;

  /**
   * State of the Supervisor/component after the component has indicated it could not initialize or startup
   * successfully.
   */
  public static final LifecycleState LifecycleFailure =  LifecycleInitializeFailure$.MODULE$;

  /**
   * State of the Supervisor/component after the component has indicated it is ready to shutdown after receiving
   * the [[csw.services.pkg.Supervisor.ShutdownComplete]] message.
   */
  public static final LifecycleState LifecycleShutdown =  LifecycleShutdown$.MODULE$;

  /**
   * State of the Supervisor/component when the component indicated it could not get ready to shutdown or failed
   * to notify the Supervisor with the [[csw.services.pkg.Supervisor.ShutdownComplete]] message within the
   * timeout.
   */
  public static final LifecycleState LifecycleShutdownFailure =  LifecycleShutdownFailure$.MODULE$;

  /**
   * Diagnostic message to shutdown and then exit supervisor/component
   */
  public static final FromComponentLifecycleMessage HaltComponent = HaltComponent$.MODULE$;


  // --- Messages sent to components to notify of lifecycle changes ---

  // Someone has requested that the component shutdown
  public static final ToComponentLifecycleMessage DoShutdown =  DoShutdown$.MODULE$;

  // Someone has requested that the component restart by going back to uninitialized
  public static final ToComponentLifecycleMessage DoRestart = DoRestart$.MODULE$;

  // Supervisor reports that component is in Running and Online
  public static final ToComponentLifecycleMessage Running = Running$.MODULE$;

  // Supervisor reports that compoentn is Running but is Offline
  public static final ToComponentLifecycleMessage RunningOffline = RunningOffline$.MODULE$;


  // --- Messages from component indicating events ---

  // Component indicates it has Initialized successfully
  public static final FromComponentLifecycleMessage Initialized =  Initialized$.MODULE$;

  /**
   * Component indicates it has completed shutting down successfully
   */
  public static final FromComponentLifecycleMessage ShutdownComplete = ShutdownComplete$.MODULE$;

  // -- Messages sent to a component from outside --

  /**
   * External request to restart component initialization -- only possible in LifecycleRunning and LifecycleRunningOffline
   */
  public static final SupervisorExternalMessage ExComponentRestart = ExComponentRestart$.MODULE$;

  /**
   * Diagnostic message to shutdown and then exit supervisor/component
   */
  public static final SupervisorExternalMessage ExComponentShutdown = ExComponentShutdown$.MODULE$;

  /**
   * External request to put component onlne -- only possible in LifecycleRunningOffline
   */
  public static final SupervisorExternalMessage ExComponentOnline = ExComponentOnline$.MODULE$;

  /**
   * External request to put component offline -- only possible in LifecycleRunning
   */
  public static final SupervisorExternalMessage ExComponentOffline = ExComponentOffline$.MODULE$;
}
