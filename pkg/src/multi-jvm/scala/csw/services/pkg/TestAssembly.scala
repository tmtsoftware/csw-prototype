package csw.services.pkg

import akka.actor.ActorRef
import csw.services.ccs.{AssemblyController, HcdController, HcdStatusMatcherActor, SequentialExecutor}
import csw.services.ccs.SequentialExecutor.ExecuteOne
import csw.services.ccs.Validation._
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.LocationService.ResolvedAkkaLocation
import csw.services.loc.LocationSubscriberActor
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.Supervisor._
import csw.util.akka.PublisherActor
import csw.util.akka.PublisherActor.Subscribe
import csw.util.config.StateVariable._
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}

/**
  * A test assembly that just forwards configs to HCDs based on prefix
  *
  * @param info contains information about the assembly and the components it depends on
  */
case class TestAssembly(info: AssemblyInfo, supervisor: ActorRef)
  extends Assembly with AssemblyController with PublisherActor[CurrentStates] {

  // The HCD actors (located via the location service)
  private var connections: Map[AkkaConnection, ResolvedAkkaLocation] = Map.empty

  // Holds the current HCD states, used to answer requests
  private var stateMap = Map[String, CurrentState]()

  // This tracks the HCDs
  private val trackerSubscriber = context.actorOf(LocationSubscriberActor.props)
  trackerSubscriber ! LocationSubscriberActor.Subscribe
  LocationSubscriberActor.trackConnections(info.connections, trackerSubscriber)

  supervisor ! Initialized

  override def receive: Receive = publisherReceive orElse controllerReceive orElse {
    // Receive the HCD's location
    case l: ResolvedAkkaLocation =>
      connections += l.connection -> l
      if (l.actorRef.isDefined) {
        log.info(s"Got actorRef: ${l.actorRef.get}")
        if (connections.size == 2 && connections.values.forall(_.isResolved))
          supervisor ! Started

        // XXX TODO FIXME: replace with telemetry
        l.actorRef.get ! Subscribe
      }

    // Current state received from one of the HCDs
    case s: CurrentState ⇒ updateCurrentState(s)

    case Running =>
      log.info("Received running")
    case RunningOffline =>
      log.info("Received running offline")
    case DoRestart =>
      log.info("Received dorestart")
    case DoShutdown =>
      log.info("Received doshutdown")
      // Just say complete for now
      supervisor ! ShutdownComplete
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      log.info(s"Received failed state: $state for reason: $reason")

    // Message from SequentialExecutor (used below) to execute a single setup config
    case ExecuteOne(sc, commandOriginator) =>
      for (hcdActorRef <- getActorRefs(sc.prefix)) {
        // Submit the config to the HCD
        hcdActorRef ! HcdController.Submit(sc)
        // If a commandOriginator was given, start a matcher actor that will reply with the command status
        commandOriginator.foreach { replyTo =>
          context.actorOf(HcdStatusMatcherActor.props(List(DemandState(sc)), Set(hcdActorRef), replyTo))
        }
      }

    case x => log.error(s"Unexpected message: $x")
  }

  // Current state received from one of the HCDs: Send it, together with the other states,
  // to the subscribers.
  private def updateCurrentState(s: CurrentState): Unit = {
    stateMap += s.prefix -> s
    requestCurrent()
  }

  // For now, when the current state is requested, send the HCD states.
  // TODO: Use assembly specific state
  override protected def requestCurrent(): Unit = {
    //    stateMap.values.foreach(notifySubscribers)
    notifySubscribers(CurrentStates(stateMap.values.map(identity).toSeq))
  }

  override def setup(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ValidationList = {
    // Returns validations for all
    val validations: ValidationList = validateSequenceConfigArg(sca)
    if (isAllValid(validations)) {
      context.actorOf(SequentialExecutor.props(self, sca, commandOriginator))
    }
    validations
  }

  /**
    * Returns a set of ActorRefs for the components that are resolved and match the config's prefix
    */
  private def getActorRefs(targetPrefix: String): Set[ActorRef] = {
    val locations = connections.values.toSet
    val x = locations.collect {
      case ResolvedAkkaLocation(_, _, prefix, actorRefOpt) if prefix == targetPrefix => actorRefOpt
    }
    x.flatten
  }

  /**
    * Performs the initial validation of the incoming SetupConfgiArg
    */
  private def validateSequenceConfigArg(sca: SetupConfigArg): ValidationList = {
    sca.configs.map(config => validateOneSetupConfig(config)).toList
  }

  private def validateOneSetupConfig(sc: SetupConfig): Validation = {
    if (sc.exists(TestConfig.posName)) Valid
    else Invalid(WrongConfigKeyIssue("Expected a posName key"))
  }
}
