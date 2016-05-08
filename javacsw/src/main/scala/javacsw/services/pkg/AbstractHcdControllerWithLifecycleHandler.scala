package javacsw.services.pkg

import java.util.Optional
import java.util.function.BiFunction

import collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.compat.java8.FunctionConverters._
import akka.actor.{AbstractActor, ActorLogging, ActorRef}
import akka.util.Timeout
import csw.services.ccs.{AssemblyController, HcdController}
import csw.services.ccs.AssemblyController.Validation
import csw.services.loc.Connection
import csw.services.loc.LocationService.Location
import csw.services.pkg.{Assembly, Hcd, LifecycleHandler}
import csw.util.cfg.Configurations.{ObserveConfigArg, SetupConfig, SetupConfigArg}
import csw.util.cfg.RunId
import csw.util.cfg.StateVariable.{CurrentState, DemandState}

/**
 * Supports Java subclasses of AssemblyController and LifecycleHandler
 */
abstract class AbstractHcdControllerWithLifecycleHandler extends AbstractActor
    with ActorLogging with Hcd with HcdController with LifecycleHandler {

  /**
   * The default actor receive method
   */
  def defaultReceive = controllerReceive orElse lifecycleHandlerReceive

  // -- These methods use Java types (Set, List, Optional, BiFunction) rather than the Scala counterparts --

  // -- Called from parent --

  override def requestCurrent(): Unit

  override def process(config: SetupConfig): Unit

  // -- These can be called from Java based subclasses

  override protected def notifySubscribers(a: CurrentState): Unit = super.notifySubscribers(a)
}
