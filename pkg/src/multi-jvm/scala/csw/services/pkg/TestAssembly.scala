package csw.services.pkg

import akka.actor.ActorRef
import csw.services.ccs.AssemblyController
import csw.services.ccs.Validation._
import csw.services.loc.LocationService.Location
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.Supervisor.{Initialized, Started}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

import scala.concurrent.Future
/**
  * A test assembly that just forwards configs to HCDs based on prefix
  *
  * @param info contains information about the assembly and the components it depends on
  */
case class TestAssembly(info: AssemblyInfo, supervisor: ActorRef)
  extends Assembly with AssemblyController with LifecycleHandler {

  import AssemblyController._

  supervisor ! Initialized
  supervisor ! Started

  // Get the connections to the HCDs this assembly uses and track them
  trackConnections(info.connections)

  log.info("Message from TestAssembly")

  override def receive: Receive = controllerReceive orElse lifecycleHandlerReceive orElse {
    // Current state received from one of the HCDs: Just forward it to the assembly, which subscribes to the HCD's status
    case s: CurrentState =>

    case x => log.error(s"Unexpected message: $x")
  }

  /**
    * Called when all HCD locations are resolved.
    * Overridden here to subscribe to status values from the HCDs.
    */
  override protected def allResolved(locations: Set[Location]): Unit = {
    subscribe(locations)
  }

  /**
    * Validates a received config arg
    */
  private def validate(config: SetupConfigArg): Validation = {
    // Checks a single setup config
    def validateConfig(sc: SetupConfig): Validation = {
      if (sc.configKey.prefix != TestConfig.testConfig1.configKey.prefix
        && sc.configKey.prefix != TestConfig.testConfig2.configKey.prefix) {
        Invalid(WrongConfigKeyIssue("Wrong prefix"))
      } else {
        val missing = sc.missingKeys(TestConfig.posName, TestConfig.c1, TestConfig.c2, TestConfig.equinox)
        if (missing.nonEmpty)
          Invalid(MissingKeyIssue(s"Missing keys: ${missing.mkString(", ")}"))
        else Valid
      }
    }
    val list = config.configs.map(validateConfig).filter(_ != Valid)
    if (list.nonEmpty) list.head else Valid // XXX TODO FIXME: Return list
  }

  override protected def setup(locationsResolved: Boolean, configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)
    if (valid == Valid) {
      // The call below just distributes the configs to the HCDs based on matching prefix,
      // but you could just as well generate new configs and send them here...
      distributeSetupConfigs(locationsResolved, configArg, replyTo)
    } else valid
  }

  // Implement the request feature to return dummy data
  override protected def request(locationsResolved: Boolean, config: SetupConfig): Future[RequestResult] = {
    Future.successful(RequestResult(RequestOK, Some(TestConfig.testConfig2)))
  }

}

