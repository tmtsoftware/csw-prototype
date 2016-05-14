package csw.services.pkg

import akka.actor.ActorRef
import csw.services.ccs.{AssemblyController, HcdController, StateVariableMatcherActor}
import csw.services.pkg.Component.AssemblyInfo
import csw.util.cfg.StateVariable.DemandState
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

/**
  * A test assembly that just forwards configs to HCDs based on prefix
  *
  * @param info contains information about the assembly and the components it depends on
  */
case class TestAssembly(info: AssemblyInfo)
  extends Assembly with AssemblyController with LifecycleHandler {

  import AssemblyController._
  import Supervisor._
  lifecycle(supervisor)

  // Get the connections to the HCDs this assembly uses and track them
  trackConnections(info.connections)

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

  override protected def setup(locationsResolved: Boolean, configArg: SetupConfigArg,
                      replyTo: Option[ActorRef]): Validation = {
    val valid = validate(configArg)
    if (valid.isValid) {
      // The code below just distributes the configs to the HCDs based on matching prefix,
      // but you could just as well generate new configs and send them here...
      val demandStates = for {
        config ← configArg.configs
        actorRef ← getActorRefs(config.prefix)
      } yield {
        actorRef ! HcdController.Submit(config)
        DemandState(config)
      }
      replyTo.foreach { actorRef =>
        // Wait for the demand states to match the current states, then reply to the sender with the command status
        context.actorOf(StateVariableMatcherActor.props(demandStates.toList, actorRef, configArg.info.runId))
      }
    }
    valid
  }
}

