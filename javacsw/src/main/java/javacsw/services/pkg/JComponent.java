package javacsw.services.pkg;

import akka.actor.Actor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import csw.services.loc.Connection;
import csw.services.loc.ConnectionType;
import csw.services.pkg.Component;
import csw.services.pkg.Component$;
import csw.services.pkg.Component.*;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.Set;

/**
 * Java API to CSW components, such as an assembly, HCD (Hardware Control Daemon) or SC (Sequence Component).
 * <p>
 * Each component has its own ActorSystem, LifecycleManager and name.
 */
@SuppressWarnings({"unused", "SameParameterValue", "OptionalUsedAsFieldOrParameterType"})
public interface JComponent extends Actor {

  // -- LocationServiceUsage: Describes how a component uses the location service --
  LocationServiceUsage DoNotRegister = Component.DoNotRegister$.MODULE$;
  LocationServiceUsage RegisterOnly = Component.RegisterOnly$.MODULE$;
  LocationServiceUsage RegisterAndTrackServices = Component.RegisterAndTrackServices$.MODULE$;

  /**
   * Describes an HCD component
   *
   * @param componentName        name used to register the component with the location service
   * @param prefix               the configuration prefix (part of configs that component should receive)
   * @param componentClassName   The name of the class that implements the component (used to create the class via reflection)
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param rate                 the HCD's refresh rate
   * @return the HcdInfo object
   */
  static HcdInfo hcdInfo(String componentName,
                         String prefix,
                         String componentClassName,
                         LocationServiceUsage locationServiceUsage,
                         Set<ConnectionType> registerAs,
                         FiniteDuration rate) {
    return JComponentSup.hcdInfo(componentName, prefix, componentClassName, locationServiceUsage, registerAs, rate);
  }

  /**
   * Describes an Assembly component
   *
   * @param componentName        name used to register the component with the location service
   * @param prefix               the configuration prefix (part of configs that component should receive)
   * @param componentClassName   The name of the class that implements the component (used to create the class via reflection)
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param connections          a list of connections that includes componentIds and connection Types
   * @return the AssemblyInfo object
   */
  static AssemblyInfo assemblyInfo(String componentName,
                                   String prefix,
                                   String componentClassName,
                                   LocationServiceUsage locationServiceUsage,
                                   Set<ConnectionType> registerAs,
                                   Set<Connection> connections) {
    return JComponentSup.assemblyInfo(componentName, prefix, componentClassName, locationServiceUsage, registerAs, connections);
  }

  /**
   * Describes a container component.
   *
   * @param componentName        name used to register the component with the location service
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param componentInfos       information about the components contained in the container
   * @param initialDelay         only for testing
   * @param creationDelay        only for testing
   * @param lifecycleDelay       only for testing
   * @return the ContainerInfo object
   */
  static ContainerInfo containerInfo(String componentName,
                                     LocationServiceUsage locationServiceUsage,
                                     Set<ConnectionType> registerAs,
                                     Set<ComponentInfo> componentInfos,
                                     FiniteDuration initialDelay,
                                     FiniteDuration creationDelay,
                                     FiniteDuration lifecycleDelay) {
    return JComponentSup.containerInfo(componentName, locationServiceUsage, registerAs, componentInfos, initialDelay, creationDelay, lifecycleDelay);
  }

  /**
   * Java API to create a component from the given componentInfo
   *
   * @param context       the actor context
   * @param componentInfo describes the component
   * @param supervisorIn  optional supervisor actor to use instead of the default
   * @return the component's supervisor
   */
  static ActorRef create(ActorContext context, ComponentInfo componentInfo, Optional<ActorRef> supervisorIn) {
    return Component$.MODULE$.create(context, componentInfo, supervisorIn);
  }
}


