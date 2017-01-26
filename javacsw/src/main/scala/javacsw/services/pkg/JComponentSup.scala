package javacsw.services.pkg

import csw.services.loc.{Connection, ConnectionType}
import csw.services.pkg.Component._

import scala.concurrent.duration._
import collection.JavaConverters._

/**
 * Java API support for CSW components, such as an assembly, HCD (Hardware Control Daemon) or SC (Sequence Component).
 *
 * Each component has its own ActorSystem and name.
 */
object JComponentSup {

  /**
   * Describes an HCD component
   *
   * @param componentName        name used to register the component with the location service
   * @param prefix               the configuration prefix (part of configs that component should receive)
   * @param componentClassName   The name of the class that implements the component (used to create the class via reflection)
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param rate                 the HCD's refresh rate
   */
  def hcdInfo(
    componentName:        String,
    prefix:               String,
    componentClassName:   String,
    locationServiceUsage: LocationServiceUsage,
    registerAs:           java.util.Set[ConnectionType],
    rate:                 FiniteDuration
  ): HcdInfo =
    HcdInfo(componentName, prefix, componentClassName, locationServiceUsage, registerAs.asScala.toSet, rate)

  /**
   * Describes an Assembly component
   *
   * @param componentName        name used to register the component with the location service
   * @param prefix               the configuration prefix (part of configs that component should receive)
   * @param componentClassName   The name of the class that implements the component (used to create the class via reflection)
   * @param locationServiceUsage how the component plans to use the location service
   * @param registerAs           register as an akka or http component or both
   * @param connections          a list of connections that includes componentIds and connection Types
   */
  def assemblyInfo(
    componentName:        String,
    prefix:               String,
    componentClassName:   String,
    locationServiceUsage: LocationServiceUsage,
    registerAs:           java.util.Set[ConnectionType],
    connections:          java.util.Set[Connection]
  ): AssemblyInfo =
    AssemblyInfo(componentName, prefix, componentClassName, locationServiceUsage, registerAs.asScala.toSet,
      connections.asScala.toSet)

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
   */
  def containerInfo(
    componentName:        String,
    locationServiceUsage: LocationServiceUsage,
    registerAs:           java.util.Set[ConnectionType],
    componentInfos:       java.util.Set[ComponentInfo],
    initialDelay:         FiniteDuration                = 0.seconds,
    creationDelay:        FiniteDuration                = 0.seconds,
    lifecycleDelay:       FiniteDuration                = 0.seconds
  ): ContainerInfo =
    ContainerInfo(componentName, locationServiceUsage, registerAs.asScala.toSet, componentInfos.asScala.toSet,
      initialDelay, creationDelay, lifecycleDelay)
}

