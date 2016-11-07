package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.services.ccs.SequentialExecution;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.loc.LocationSubscriberActor;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor3;
import javacsw.services.alarms.IAlarmService;
import javacsw.services.events.IEventService;
import javacsw.services.events.ITelemetryService;
import javacsw.services.loc.JComponentType;
import javacsw.services.loc.JLocationSubscriberActor;
import csw.examples.vsliceJava.assembly.TromboneStateActor.TromboneStateClient;
import csw.examples.vsliceJava.assembly.AssemblyContext.TromboneCalculationConfig;
import csw.examples.vsliceJava.assembly.AssemblyContext.TromboneControlConfig;
import javacsw.services.pkg.JAssemblyControllerWithLifecycleHandler;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import csw.services.loc.LocationService.Location;
import csw.services.loc.LocationService.ResolvedAkkaLocation;
import csw.services.loc.LocationService.ResolvedHttpLocation;
import csw.services.loc.LocationService.ResolvedTcpLocation;
import csw.services.loc.LocationService.Unresolved;
import csw.services.loc.LocationService.UnTrackedLocation;
import csw.util.config.Configurations.SetupConfigArg;
import csw.services.ccs.Validation;
import csw.services.loc.Connection.TcpConnection;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static javacsw.services.pkg.JSupervisor3.*;

/**
 * TMT Source Code: 6/10/16.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public class TromboneAssembly extends JAssemblyControllerWithLifecycleHandler implements TromboneStateClient  {

  private final Component.AssemblyInfo info;
  private final ActorRef supervisor;
  private ActorRef tromboneHCD;
  private final DiagnosticLoggingAdapter log = log();
  private final AssemblyContext ac;
  private final ActorRef commandHandler;

  @SuppressWarnings("FieldCanBeLocal")
  private TromboneStateActor.TromboneState internalState = TromboneStateActor.defaultTromboneState;

  @Override
  public void setCurrentState(TromboneStateActor.TromboneState ts) {
    internalState = ts;
  }

  @Override
  public String prefix() {
    return info.prefix();
  }

  public TromboneAssembly(Component.AssemblyInfo info, ActorRef supervisor) {
    super(info);
    this.info = info;
    this.supervisor = supervisor;
    tromboneHCD = context().system().deadLetters();

    System.out.println("INFO: " + info);
//  // Start tracking the components we command
    log.info("Connections: " + info.connections());

    // Get the assembly configuration from the config service or resource file (XXX TODO: Change to be non-blocking like the HCD version)
    TromboneConfigs configs = getAssemblyConfigs();
    ac = new AssemblyContext(info, configs.calculationConfig, configs.controlConfig);

//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());

    // Initial receive - start with initial values
    getContext().become(initializingReceive());


    ActorRef trackerSubscriber = context().actorOf(LocationSubscriberActor.props());
    trackerSubscriber.tell(JLocationSubscriberActor.Subscribe, self());
    LocationSubscriberActor.trackConnections(info.connections(), trackerSubscriber);
    LocationSubscriberActor.trackConnection(TromboneAssembly.eventServiceConnection, trackerSubscriber);
    LocationSubscriberActor.trackConnection(TromboneAssembly.alarmServiceConnection, trackerSubscriber);

    // This actor handles all telemetry and system event publishing
    ActorRef eventPublisher = context().actorOf(TrombonePublisher.props(ac, Optional.empty()));

    // Setup command handler for assembly - note that CommandHandler connects directly to tromboneHCD here, not state receiver
    commandHandler = context().actorOf(TromboneCommandHandler.props(ac, Optional.of(tromboneHCD), Optional.of(eventPublisher)));

    supervisor.tell(Initialized, self());
  }

  /**
   * This contains only commands that can be received during intialization
   *
   * @return Receive is a partial function
   */
  private PartialFunction<Object, BoxedUnit> initializingReceive() {
    return ReceiveBuilder.
      match(Location.class, this::lookAtLocations).
      matchEquals(Running, location -> {
        // When Running is received, transition to running Receive
        log.info("becoming runningReceive");
        context().become(runningReceive());
      }).
      matchAny(t -> log.warning("Unexpected message in TromboneAssembly:initializingReceive: " + t)).
      build();
  }

  private void lookAtLocations(Location location) {
    if (location instanceof ResolvedAkkaLocation) {
      ResolvedAkkaLocation l = (ResolvedAkkaLocation) location;
      log.info("Got actorRef: " + l.actorRef());
      tromboneHCD = l.getActorRef().orElse(context().system().deadLetters());
      supervisor.tell(Started, self());
    } else if (location instanceof ResolvedHttpLocation) {
      log.info("HTTP Service Damn it: " + location.connection());
    } else if (location instanceof ResolvedTcpLocation) {
      log.info("Service resolved: " + location.connection());
    } else if (location instanceof Unresolved) {
      log.info("Unresolved: " + location.connection());
    } else if (location instanceof UnTrackedLocation) {
      log.info("UnTracked: " + location.connection());
    }
  }

  private PartialFunction<Object, BoxedUnit> locationReceive() {
    return ReceiveBuilder.
      match(Location.class, this::lookAtLocations).
      build();
  }

  private PartialFunction<Object, BoxedUnit> runningReceive() {
    return locationReceive().orElse(stateReceive()).orElse(controllerReceive()).orElse(lifecycleReceivePF()).orElse(unhandledPF());
  }

  private PartialFunction<Object, BoxedUnit> lifecycleReceivePF() {
    return ReceiveBuilder.
      matchEquals(Running, t -> {
        // Already running so ignore
      }).
      matchEquals(RunningOffline, t -> {
        // Here we do anything that we need to do be an offline, which means running and ready but not currently in use
        log.info("Received running offline");
      }).
      matchEquals(DoRestart, t -> log.info("Received dorestart")).
      matchEquals(DoShutdown, t -> {
        log.info("Received doshutdown");
        // Ask our HCD to shutdown, then return complete
        tromboneHCD.tell(DoShutdown, self());
        supervisor.tell(ShutdownComplete, self());
      }).
      match(Supervisor3.LifecycleFailureInfo.class, t -> {
        // This is an error condition so log it
        log.error("TromboneAssembly received failed lifecycle state: " + t.state() + " for reason: " + t.reason());
      }).
      build();
  }

  private PartialFunction<Object, BoxedUnit> unhandledPF() {
    return ReceiveBuilder.
      matchAny(t -> log.warning("Unexpected message in TromboneAssembly:unhandledPF: " + t)).
      build();
  }

  /**
   * Validates a received config arg and returns the first
   */
  private List<Validation.Validation> validateSequenceConfigArg(SetupConfigArg sca) {
    // Are all of the configs really for us and correctly formatted, etc?
    return ConfigValidation.validateTromboneSetupConfigArg(sca, ac);
  }

  @Override
  public List<Validation.Validation> setup(SetupConfigArg sca, Optional<ActorRef> commandOriginator) {
    // Returns validations for all
    List<Validation.Validation> validations = validateSequenceConfigArg(sca);
    if (Validation.isAllValid(validations)) {
      if (sca.jconfigs().size() == 1 && sca.jconfigs().get(0).configKey().equals(ac.stopCK)) {
        // Special handling for stop which needs to interrupt the currently executing sequence
        commandHandler.tell(sca.jconfigs().get(0), self());
      } else {
        ActorRef executor = newExecutor(sca, commandOriginator);
        executor.tell(new SequentialExecution.SequentialExecutor.StartTheSequence(commandHandler), self());
      }
    }
    return validations;
  }

  private ActorRef newExecutor(SetupConfigArg sca, Optional<ActorRef> commandOriginator) {
    return context().actorOf(SequentialExecution.SequentialExecutor.props(sca, commandOriginator));
  }

  // Holds the assembly configurations
  private static class TromboneConfigs {
    final TromboneCalculationConfig calculationConfig;
    final TromboneControlConfig controlConfig;

    TromboneConfigs(TromboneCalculationConfig tromboneCalculationConfig, TromboneControlConfig tromboneControlConfig) {
      this.calculationConfig = tromboneCalculationConfig;
      this.controlConfig = tromboneControlConfig;
    }
  }

  // Gets the assembly configurations from the config service, or a resource file, if not found and
  // returns the two parsed objects.
  private TromboneConfigs getAssemblyConfigs() {
    // Get the trombone config file from the config service, or use the given resource file if that doesn't work
    File tromboneConfigFile = new File("trombone/tromboneAssembly.conf");
    File resource = new File("tromboneAssembly.conf");

    // XXX TODO: Use config service (deal with timeout issues, if not running: Note: tests wait for 3 seconds...)
    //    implicit val timeout = Timeout(1.seconds)
    //    val f = ConfigServiceClient.getConfigFromConfigService(tromboneConfigFile, resource = Some(resource))
    //    // parse the future (optional) config (XXX waiting for the result for now, need to wait longer than the timeout: FIXME)
    //    Await.result(f.map(configOpt => (TromboneCalculationConfig(configOpt.get), TromboneControlConfig(configOpt.get))), 2.seconds)

    Config config = ConfigFactory.parseResources(resource.getPath());
    System.out.println("Config: " + config);
    return new TromboneConfigs(new TromboneCalculationConfig(config), new TromboneControlConfig(config));
  }

  // --- Static defs ---

  public static Props props(Component.AssemblyInfo assemblyInfo, ActorRef supervisor) {
    return Props.create(new Creator<TromboneAssembly>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneAssembly create() throws Exception {
        return new TromboneAssembly(assemblyInfo, supervisor);
      }
    });
  }


  // --------- Keys/Messages used by Multiple Components

  /**
   * The message is used within the Assembly to update actors when the Trombone HCD goes up and down and up again
   */
  @SuppressWarnings("WeakerAccess")
  public static class UpdateTromboneHCD {
    public final Optional<ActorRef> tromboneHCD;

    /**
     * @param tromboneHCD the ActorRef of the tromboneHCD or None
     */
    public UpdateTromboneHCD(Optional<ActorRef> tromboneHCD) {
      this.tromboneHCD = tromboneHCD;
    }
  }

  /**
   * Services needed by Trombone Assembly
   */
  private static final Connection eventServiceConnection = new TcpConnection(new ComponentId(IEventService.defaultName, JComponentType.Service));
  // Kim update when telmetry service merged
  private static final Connection telemetryServiceConnection = new TcpConnection(new ComponentId(ITelemetryService.defaultName, JComponentType.Service));
  private static final Connection alarmServiceConnection = new TcpConnection(new ComponentId(IAlarmService.defaultName, JComponentType.Service));
}
