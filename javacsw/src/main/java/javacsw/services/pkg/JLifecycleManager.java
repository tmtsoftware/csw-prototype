package javacsw.services.pkg;

import csw.services.pkg.LifecycleManager;
import csw.services.pkg.LifecycleManager.*;

/**
 * Java API for LifecycleManager
 */
public class JLifecycleManager {
    // -- Commands sent to components to change the lifecycle --
    public static final LifecycleCommand Load = Load$.MODULE$;
    public static final LifecycleCommand Initialize = Initialize$.MODULE$;
    public static final LifecycleCommand Startup = Startup$.MODULE$;
    public static final LifecycleCommand Shutdown = Shutdown$.MODULE$;
    public static final LifecycleCommand Remove = Remove$.MODULE$;
    public static final LifecycleCommand Heartbeat = Heartbeat$.MODULE$;

    public static LifecycleCommand LifecycleFailure(LifecycleState state, String reason) {
        return LifecycleFailure$.MODULE$.apply(state, reason);
    }

}

//
//        sealed trait FSMData
//
//        case object TargetLoaded extends FSMData
//
//        case object TargetPendingInitializedFromLoaded extends FSMData
//
//        case object TargetPendingLoadedFromInitialized extends FSMData
//
//        case object TargetInitialized extends FSMData
//
//        case object TargetPendingRunningFromInitialized extends FSMData
//
//        case object TargetPendingInitializedFromRunning extends FSMData
//
//        case object TargetRunning extends FSMData
//
//        case class FailureInfo(state: LifecycleState, reason: String) extends FSMData
//
//        /**
//         * Reply from component for failed lifecycle changes
//         */
//        sealed trait LifecycleResponse
//
//        case class InitializeFailure(reason: String) extends LifecycleResponse
//
//        case object InitializeSuccess extends LifecycleResponse
//
//        case class StartupFailure(reason: String) extends LifecycleResponse
//
//        case object StartupSuccess extends LifecycleResponse
//
//        case class ShutdownFailure(reason: String) extends LifecycleResponse
//
//        case object ShutdownSuccess extends LifecycleResponse
//
//        case class UninitializeFailure(reason: String) extends LifecycleResponse
//
//        case object UninitializeSuccess extends LifecycleResponse
//
//        def props(component: ActorRef, name: String): Props =
//        Props(classOf[LifecycleManager], component, name)
//        }
