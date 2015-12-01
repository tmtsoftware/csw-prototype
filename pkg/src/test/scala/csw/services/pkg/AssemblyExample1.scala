package csw.services.pkg

import akka.actor.{Props, ActorRef}
import csw.services.ccs.{StateMatcherActor, HcdController, AssemblyController}
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.ResolvedService
import csw.services.loc.{ServiceId, ServiceType, ServiceRef}
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
          DemandState(config)
        }
        replyTo.foreach { actorRef =>
          // Wait for the demand states to match the current states, then reply to the sender with the command status
          context.actorOf(StateMatcherActor.props(demandStates.toList, actorRef, configArg.info.runId))
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
}

/**
  * Starts Hcd2 as a standalone application.
  * Args: name, configPath
  */
object Assembly1ExampleApp extends App {

  println("Starting! Assemb1")
  val name = "assembly1"
  val prefix = "tcs.pos.assem"
  val serviceId = ServiceId(name, ServiceType.Assembly)

  val targetServiceId = ServiceId("example1", ServiceType.HCD)

  val props = Props(classOf[Assembly1], name, prefix)
  //val cname = classOf[HCDDaemon].getName
  //println("Name: " + cname)

  val compInfo: ComponentInfo = Component.create(props, serviceId, prefix, List(targetServiceId))
}


