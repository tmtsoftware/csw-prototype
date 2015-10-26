package csw.services.pkg

import akka.actor.ActorRef
import csw.services.ccs.{HcdController, StateMatcherActor, AssemblyController}
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.ResolvedService
import csw.services.loc.ServiceRef
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}
import csw.util.cfg.TestConfig

// A test assembly that just forwards configs to HCDs based on prefix
case class TestAssembly(name: String) extends Assembly with AssemblyController with LifecycleHandler {

  import AssemblyController._

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
      // The code below just distributes the configs to the HCDs based on matching prefix,
      // but you could just as well generate new configs and send them here...
      val demandStates = for {
        config ← configArg.configs
        service ← services.values.find(v ⇒ v.prefix == config.configKey.prefix && v.serviceRef.accessType == AkkaType)
        actorRef ← service.actorRefOpt
      } yield {
        actorRef ! HcdController.Submit(config)
        config
      }
      replyTo.foreach { actorRef =>
        // Wait for the demand states to match the current states, then reply to the sender with the command status
        context.actorOf(StateMatcherActor.props(demandStates.toList, actorRef, configArg.info.runId))
      }
    }
    valid
  }
}

