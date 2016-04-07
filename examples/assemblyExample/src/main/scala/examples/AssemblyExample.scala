package examples

import java.net.URI

import akka.actor.{ActorRef, Props}
import csw.services.ccs.{AssemblyController, CommandStatus, HcdController}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.loc.{ComponentId, ComponentType, Connection, LocationService}
import csw.services.pkg.{Assembly, Component, LifecycleHandler}
import csw.services.pkg.Component.ComponentInfo
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

/**
 * An example assembly
 */
object Assembly1 {
  val assemblyName = "assembly1"

  // Used to lookup the HCD this assembly uses
  val targetConnection = AkkaConnection(ComponentId(HCDExample.hcdName, ComponentType.HCD))

  /**
   * Returns a config for setting the rate
   */
  def getRateConfig(rate: Int): SetupConfigArg = {
    val sc = SetupConfig(HCDExample.prefix).set(HCDExample.rateKey, rate)
    SetupConfigArg("test", sc)
  }

  /**
   * Used to create the assembly actor
   */
  def props(): Props = Props(classOf[Assembly1])
}

class Assembly1 extends Assembly with AssemblyController with LifecycleHandler {
  import AssemblyController._

  val name = Assembly1.assemblyName

  override def receive: Receive = controllerReceive orElse lifecycleHandlerReceive orElse {
    case x => log.error(s"Unexpected message: $x")
  }

  /**
   * Validates a received config arg
   */
  private def validate(config: SetupConfigArg): Validation = {

    // Checks a single setup config
    def validateConfig(sc: SetupConfig): Validation = {
      if (sc.configKey.prefix != HCDExample.prefix) {
        Invalid("Wrong prefix")
      } else {
        val missing = sc.data.missingKeys(HCDExample.rateKey)
        if (missing.nonEmpty)
          Invalid(s"Missing keys: ${missing.mkString(", ")}")
        else Valid
      }
    }

    val list = config.configs.map(validateConfig).filter(!_.isValid)
    if (list.nonEmpty) list.head else Valid
  }

  override protected def setup(locationsResolved: Boolean, configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)
    if (valid.isValid) {
      // Get a reference to the actor for the HCD
      val hcdActorRefOpt = getLocation(Assembly1.targetConnection).flatMap {
        case ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) => actorRefOpt
      }
      // Submit each config
      for {
        config <- configArg.configs
        hcdActorRef <- hcdActorRefOpt
      } {
        hcdActorRef ! HcdController.Submit(config)
      }

      // If a replyTo actor was given, reply with the command status
      if (replyTo.isDefined) {
        replyTo.get ! CommandStatus.Completed(configArg.info.runId)
      }
    }
    valid
  }
}

/**
 * Starts Hcd as a standalone application.
 */
object AssemblyExampleApp extends App {
  import Assembly1._
  println("Starting Assembly1")
  LocationService.initInterface()
  val componentId = ComponentId(assemblyName, ComponentType.Assembly)
  val targetComponentId = targetConnection.componentId
  val props = Assembly1.props()
  val compInfo: ComponentInfo = Component.create(props, componentId, "", List(targetComponentId))
}
