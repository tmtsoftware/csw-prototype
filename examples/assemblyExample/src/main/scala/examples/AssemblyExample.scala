package examples

import akka.actor.{Props, ActorRef}
import csw.services.ccs.{CommandStatus, HcdController, AssemblyController}
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.ResolvedService
import csw.services.loc.{LocationService, ServiceId, ServiceType, ServiceRef}
import csw.services.pkg.{Component, LifecycleHandler, Assembly}
import csw.services.pkg.Component.ComponentInfo
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

/**
 * An example assembly
 */
object Assembly1 {
  val assemblyName = "assembly1"

  // Used to lookup the HCD this assembly uses
  val targetServiceRef = ServiceRef(ServiceId(HCDExample.hcdName, ServiceType.HCD), AkkaType)

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

  override val name = Assembly1.assemblyName

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

  /**
   * Called to process the setup config and reply to the given actor with the command status.
   *
   * @param services contains information about any required services
   * @param configArg contains a list of setup configurations
   * @param replyTo if defined, the actor that should receive the final command status.
   */
  override protected def setup(services: Map[ServiceRef, ResolvedService], configArg: SetupConfigArg,
                               replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)
    if (valid.isValid) {
      // Get a reference to the actor for the HCD
      val actorRef = services(Assembly1.targetServiceRef).actorRefOpt.get

      // Submit each config
      configArg.configs.foreach { config ⇒
        actorRef ! HcdController.Submit(config)
      }

      // If a replyTo actor was given, reply with the command status
      if (replyTo.isDefined) {
        replyTo.get ! CommandStatus.Completed(configArg.info.runId)
      }
    }
    valid
  }

  override protected def connected(services: Map[ServiceRef, ResolvedService]): Unit = {
    log.info("Connected: " + services)
  }

  override protected def disconnected(): Unit = {
    log.info("Disconnected!")
  }
}

/**
 * Starts Hcd as a standalone application.
 */
object AssemblyExampleApp extends App {
  import Assembly1._
  println("Starting Assembly1")
  LocationService.initInterface()
  val serviceId = ServiceId(assemblyName, ServiceType.Assembly)
  val targetServiceId = targetServiceRef.serviceId
  val props = Assembly1.props()
  val compInfo: ComponentInfo = Component.create(props, serviceId, "", List(targetServiceId))
}
