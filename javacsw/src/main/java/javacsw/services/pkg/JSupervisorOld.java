//package javacsw.services.pkg;
//
//import akka.actor.ActorRef;
//import csw.services.pkg.Component;
//import csw.services.pkg.LifecycleManager;
//import csw.services.pkg.SupervisorOld;
//import csw.services.pkg.SupervisorOld.*;
//
///**
// * The Supervisor is an actor that supervises the component actors and deals with
// * component lifecycle messages so components don't have to. There is one Supervisor per component.
// * It registers with the location service and is responsible for starting and stopping the component
// * as well as managing its state.
// * All component messages go through the Supervisor, so it can reject any
// * messages that are not allowed in a given lifecycle.
// * <p>
// * See the TMT document "OSW TN012 - COMPONENT LIFECYCLE DESIGN" for a description of CSW lifecycles.
// */
//@SuppressWarnings("unused")
//public class JSupervisorOld {
//
//    /**
//     * Message sent to supervisor from component or elsewhere to restart lifecycle for component
//     */
//    public static final SupervisorMessage RestartComponent = RestartComponent$.MODULE$;
//
//    /**
//     * When this message is received, the component goes through shutdown, uninitialize, and then exits
//     */
//    public static final SupervisorMessage HaltComponent = HaltComponent$.MODULE$;
//
//    /**
//     * Returns a new supervisor actor managing the components described in the argument
//     *
//     * @param componentInfo describes the components to create and manage
//     * @return the actorRef for the supervisor (parent actor of the top level component)
//     */
//    public static ActorRef create(Component.ComponentInfo componentInfo) {
//      return SupervisorOld.apply(componentInfo);
//    }
//
//    /**
//     * Function called by a component to indicate the lifecycle should be started.
//     *
//     * @param supervisor the ActorRef of the component's supervisor
//     * @param command    the LifecycleCommand to be sent to the supervisor
//     */
//    public static void lifecycle(ActorRef supervisor, LifecycleManager.LifecycleCommand command) {
//        SupervisorOld.lifecycle(supervisor, command);
//    }
//
//    /**
//     * Function called by a component to indicate the lifecycle should be started.
//     *
//     * @param supervisor the ActorRef of the component's supervisor
//     */
//    public static void lifecycle(ActorRef supervisor) {
//        SupervisorOld.lifecycle(supervisor, LifecycleManager.Startup$.MODULE$);
//    }
//}
//
