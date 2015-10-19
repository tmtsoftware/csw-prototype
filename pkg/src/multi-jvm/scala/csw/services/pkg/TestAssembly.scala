package csw.services.pkg

import csw.services.ccs.{StateMatcherActor, AssemblyController}
import csw.services.loc.AccessType.AkkaType
import csw.services.loc.LocationService.ResolvedService
import csw.services.loc.ServiceRef
import csw.shared.cmd.RunId
import csw.util.cfg.Configurations.SetupConfigArg
import csw.util.cfg.Configurations.StateVariable.DemandState

// A test assembly that just forwards configs to HCDs based on prefix
case class TestAssembly(name: String) extends Assembly with AssemblyController with LifecycleHandler {

  //  val hcd2aServiceRef = ServiceRef(ServiceId("HCD-2A", ServiceType.HCD), AkkaType)
  //  val hcd2bServiceRef = ServiceRef(ServiceId("HCD-2B", ServiceType.HCD), AkkaType)

  override protected def process(services: Map[ServiceRef, ResolvedService], configArg: SetupConfigArg): Unit = {
    //    val hcd2a = services(hcd2aServiceRef)
    //    val hcd2b = services(hcd2bServiceRef)

    // The code below just distributes the configs to the HCDs based on matching prefix,
    // but you could just as well generate new configs and send them here...
    val demandStates = for {
      config ← configArg.configs
      service ← services.values.find(v ⇒ v.prefix == config.configKey.prefix && v.serviceRef.accessType == AkkaType)
      actorRef ← service.actorRefOpt
    } yield {
        actorRef ! config
        DemandState(config.configKey.prefix, config.data)
      }

    // Wait for the demand states to match the current states, then reply to the sender
    context.actorOf(StateMatcherActor.props(demandStates.toList, sender(), RunId(configArg.info.runId)))

  }
}
