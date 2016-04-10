package csw.services.pkg

import akka.actor.ActorRef
import csw.services.ccs.{AssemblyController, HcdController, StateMatcherActor}
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.pkg.Component.AssemblyInfo
import csw.util.cfg.Configurations.StateVariable.DemandState
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

/**
  * A test assembly that just forwards configs to HCDs based on prefix
  */
case class TestAssembly(info: AssemblyInfo)
  extends Assembly with AssemblyController with LifecycleHandler {

  import AssemblyController._

  import Supervisor._
  lifecycle(supervisor)

  override def receive: Receive = controllerReceive orElse lifecycleHandlerReceive orElse {
    case x => log.error(s"Unexpected message: $x")
  }

  /**
    * Validates a received config arg
    */
  private def validate(config: SetupConfigArg): Validation = {
    // Checks a single setup config
    def validateConfig(sc: SetupConfig): Validation = {
      if (sc.configKey.prefix != TestConfig.testConfig1.configKey.prefix
        && sc.configKey.prefix != TestConfig.testConfig2.configKey.prefix) {
        Invalid("Wrong prefix")
      } else {
        val missing = sc.data.missingKeys(TestConfig.posName, TestConfig.c1, TestConfig.c2, TestConfig.equinox)
        if (missing.nonEmpty)
          Invalid(s"Missing keys: ${missing.mkString(", ")}")
        else Valid
      }
    }
    val list = config.configs.map(validateConfig).filter(!_.isValid)
    if (list.nonEmpty) list.head else Valid
  }

  // Returns a set of ActorRefs for the services that are resolved and match the config's prefix
  private def getActorRefs(config: SetupConfig): Set[ActorRef] = {
    val x = getLocations.collect {
      case r@ResolvedAkkaLocation(connection, uri, prefix, actorRefOpt) if config.configKey.prefix == prefix => actorRefOpt
    }
    x.flatten
  }

  override protected def setup(locationsResolved: Boolean, configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)
    if (valid.isValid) {
      // The code below just distributes the configs to the HCDs based on matching prefix,
      // but you could just as well generate new configs and send them here...
      val demandStates = for {
        config ← configArg.configs
        actorRef ← getActorRefs(config)
      } yield {
        actorRef ! HcdController.Submit(config)
        DemandState(config)
      }
      replyTo.foreach { actorRef =>
        // Wait for the demand states to match the current states, then reply to the sender with the command status
        context.actorOf(StateMatcherActor.props(demandStates.toList, actorRef, configArg.info.runId))
      }
    }
    valid
  }
}

