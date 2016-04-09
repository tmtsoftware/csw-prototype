package csw.examples

import java.net.URI

import akka.actor.{ActorRef, Props}
import csw.services.ccs.{AssemblyController, CommandStatus, HcdController}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.loc.{ComponentId, ComponentType, Connection, LocationService}
import csw.services.pkg.Component.{AssemblyInfo, RegisterOnly}
import csw.services.pkg.{Assembly, LifecycleHandler, Supervisor}
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

/**
 * An example assembly
 */
object AssemblyExample {
  val assemblyName = "assemblyExample"
  val className = "csw.examples.AssemblyExample"

  // Used to lookup the HCD this assembly uses
  val targetHcdConnection = AkkaConnection(ComponentId(HCDExample.hcdName, ComponentType.HCD))

  /**
   * Used to create the assembly actor
   */
  def props(): Props = Props(classOf[AssemblyExample])
}

class AssemblyExample(info: AssemblyInfo) extends Assembly with AssemblyController with LifecycleHandler {
  import AssemblyController._

  val name = AssemblyExample.assemblyName

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
      val hcdActorRefOpt = getLocation(AssemblyExample.targetHcdConnection).collect {
        case ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) => actorRefOpt
      }.flatten
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
  import AssemblyExample._
  println("Starting Assembly1")
  LocationService.initInterface()
  val componentId = ComponentId(assemblyName, ComponentType.Assembly)
  val hcdConnections: Set[Connection] = Set(targetHcdConnection)
  val assemblyInfo = AssemblyInfo(assemblyName, "", className, RegisterOnly, Set(AkkaType), hcdConnections)
  val supervisor = Supervisor(assemblyInfo)
}
