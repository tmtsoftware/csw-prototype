package csw.services.pkg

import akka.actor.{Props, ActorRef}
import csw.services.ccs.{StateMatcherActor, HcdController, AssemblyController}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.loc.LocationService.ResolvedService
import csw.services.loc.{ComponentId, ComponentType, Connection}
import csw.services.pkg.AssemblyExample1.Assembly1
import csw.services.pkg.Component.ComponentInfo
import csw.services.ts.TimeService
import csw.services.ts.TimeService.TimeServiceScheduler
import csw.util.cfg.Configurations.StateVariable.DemandState
import csw.util.cfg.Configurations.{SetupConfig, SetupConfigArg}

/**
 * TMT Source Code: 11/26/15.
 */
object AssemblyExample1 {

  object Assembly1 {
    def props(name: String, prefix: String): Props = Props(classOf[Assembly1], name, prefix)
  }

  case class Assembly1(name: String, prefix: String) extends Assembly with AssemblyController with LifecycleHandler
      with TimeServiceScheduler {
    import AssemblyController._

    import TimeService._

    log.info(s"Freq: ${context.system.scheduler.maxFrequency}")

    /**
     * Validates a received config arg
     */
    private def validate(config: SetupConfigArg): Validation = {
      // Checks a single setup config
      def validateConfig(sc: SetupConfig): Validation = {
        /*
        if (sc.configKey.prefix != TestConfig.testConfig1.configKey.prefix
          && sc.configKey.prefix != TestConfig.testConfig2.configKey.prefix) {
          Invalid("Wrong prefix")
        } else {
          val missing = sc.data.missingKeys(TestConfig.posName, TestConfig.c1, TestConfig.c2, TestConfig.equinox)
          if (missing.nonEmpty)
            Invalid(s"Missing keys: ${missing.mkString(", ")}")
          else Valid
        }
        */
        Valid
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
    override protected def setup(services: Map[Connection, ResolvedService], configArg: SetupConfigArg,
                                 replyTo: Option[ActorRef]): Validation = {
      val valid = validate(configArg)
      if (valid.isValid) {
        // The code below just distributes the configs to the HCDs based on matching prefix,
        // but you could just as well generate new configs and send them here...
        val demandStates = for {
          config ← configArg.configs
          service ← services.values.find(v ⇒ v.prefix == config.configKey.prefix && v.connection.connectionType == AkkaType)
          actorRef ← service.actorRefOpt
        } yield {
          actorRef ! HcdController.Submit(config)
          DemandState(config)
        }
        replyTo.foreach { actorRef ⇒
          // Wait for the demand states to match the current states, then reply to the sender with the command status
          context.actorOf(StateMatcherActor.props(demandStates.toList, actorRef, configArg.info.runId))
        }
      }
      valid
    }

    override protected def connected(services: Map[Connection, ResolvedService]): Unit = {
      log.info("Connected: " + services)
    }

    override protected def disconnected(): Unit = {
      log.info("Disconnected!")
    }
  }
}

/**
 * Starts Hcd2 as a standalone application.
 * Args: name, configPath
 */
object Assembly1ExampleApp extends App {
  println("Starting! assembly1")
  val name = "assembly1"
  val prefix = "tcs.pos.assem"
  val componentId = ComponentId(name, ComponentType.Assembly)
  val targetComponentId = ComponentId("example1", ComponentType.HCD)
  val props = Assembly1.props(name, prefix)

  val compInfo: ComponentInfo = Component.create(props, componentId, prefix, List(targetComponentId))
}

