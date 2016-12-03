package csw.services.pkg

import akka.actor.ActorRef
import csw.services.ccs.{AssemblyController, ConfigDistributor, Validation}
import csw.services.ccs.Validation._
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.loc.LocationSubscriberActor
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.Supervisor.{Initialized, Running, Started}
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

/**
  * A test assembly that just forwards configs to HCDs based on prefix
  *
  * @param info contains information about the assembly and the components it depends on
  */
case class TestAssembly(info: AssemblyInfo, supervisor: ActorRef) extends Assembly with AssemblyController {

  // The HCD actors (located via the location service)
  private var connections: Map[AkkaConnection, ResolvedAkkaLocation] = Map.empty

  // This tracks the HCDs
  private val trackerSubscriber = context.actorOf(LocationSubscriberActor.props)
  trackerSubscriber ! LocationSubscriberActor.Subscribe
  LocationSubscriberActor.trackConnections(info.connections, trackerSubscriber)

  supervisor ! Initialized

  override def receive: Receive = controllerReceive orElse {
    // Receive the HCD's location
    case l: ResolvedAkkaLocation =>
      connections += l.connection -> l
      if (l.actorRef.isDefined) {
        log.info(s"Got actorRef: ${l.actorRef.get}")
        if (connections.size == 2 && connections.values.forall(_.isResolved))
          supervisor ! Started
      }

    case Running =>

    case x => log.error(s"Unexpected message: $x")
  }

  /**
    * Validates a received config arg
    */
  private def validateSequenceConfigArg(sca: SetupConfigArg): ValidationList = {
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
    sca.configs.map(validateConfig).toList
  }

  override def setup(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ValidationList = {
    // Returns validations for all
    val validations: ValidationList = validateSequenceConfigArg(sca)
    if (Validation.isAllValid(validations)) {
      // For this trivial test we just forward the configs to the HCDs based on prefix
      ConfigDistributor(context, connections.values.toSet).distributeSetupConfigs(sca)
    }
    validations
  }

}

