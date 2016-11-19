package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.services.ccs.SequentialExecutor;
import csw.services.ccs.Validation;
import csw.services.loc.ComponentId;
import csw.services.loc.Connection;
import csw.services.loc.LocationService.*;
import csw.services.loc.LocationSubscriberActor;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor;
import javacsw.services.alarms.IAlarmService;
import javacsw.services.events.IEventService;
import javacsw.services.events.ITelemetryService;
import javacsw.services.loc.JComponentType;
import javacsw.services.loc.JLocationSubscriberActor;
import javacsw.services.pkg.JAssemblyController;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static csw.examples.vsliceJava.assembly.AssemblyContext.TromboneCalculationConfig;
import static csw.examples.vsliceJava.assembly.AssemblyContext.TromboneControlConfig;
import static csw.services.loc.Connection.TcpConnection;
import static csw.util.config.Configurations.SetupConfigArg;
import static javacsw.services.pkg.JSupervisor.*;

/**
 * TMT Source Code: 6/10/16.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public class TromboneAssembly extends JAssemblyController {

  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final ActorRef supervisor;
  private final AssemblyContext ac;
  private final ActorRef commandHandler;

  private Optional<ActorRef> badHCDReference = Optional.empty();
  private Optional<ActorRef> tromboneHCD = badHCDReference;

  private boolean isHCDAvailable() {
    return tromboneHCD.isPresent();
  }

  private final Optional<IEventService> badEventService = Optional.empty();
  private Optional<IEventService> eventService = badEventService;

  private boolean isEventServiceAvailable() {
    return eventService.isPresent();
  }

  private final Optional<ITelemetryService> badTelemetryService = Optional.empty();
  private Optional<ITelemetryService> telemetryService = badTelemetryService;

  private boolean isTelemetryServiceAvailable() {
    return telemetryService.isPresent();
  }

  private final Optional<IAlarmService> badAlarmService = Optional.empty();
  private Optional<IAlarmService> alarmService = badAlarmService;

  private boolean isAlarmServiceAvailable() {
    return alarmService.isPresent();
  }

  public TromboneAssembly(Component.AssemblyInfo info, ActorRef supervisor) {
    super(info);
    this.supervisor = supervisor;

    System.out.println("INFO: " + info);
//  // Start tracking the components we command
    log.info("Connections: " + info.connections());

    // Get the assembly configuration from the config service or resource file (XXX TODO: Change to be non-blocking like the HCD version)
    TromboneConfigs configs = getAssemblyConfigs();
    ac = new AssemblyContext(info, configs.calculationConfig, configs.controlConfig);

    // Initial receive - start with initial values
    receive(initializingReceive());

    ActorRef trackerSubscriber = context().actorOf(LocationSubscriberActor.props());
    trackerSubscriber.tell(JLocationSubscriberActor.Subscribe, self());
    // This tracks the HCD
    LocationSubscriberActor.trackConnections(info.connections(), trackerSubscriber);
    // This tracks required services
    LocationSubscriberActor.trackConnection(TromboneAssembly.eventServiceConnection, trackerSubscriber);
    LocationSubscriberActor.trackConnection(TromboneAssembly.telemetryServiceConnection, trackerSubscriber);
    LocationSubscriberActor.trackConnection(TromboneAssembly.alarmServiceConnection, trackerSubscriber);

    // This actor handles all telemetry and system event publishing
    ActorRef eventPublisher = context().actorOf(TrombonePublisher.props(ac, Optional.empty(), Optional.empty()));

    // Setup command handler for assembly - note that CommandHandler connects directly to tromboneHCD here, not state receiver
    commandHandler = context().actorOf(TromboneCommandHandler.props(ac, tromboneHCD, Optional.of(eventPublisher)));

    supervisor.tell(Initialized, self());
  }

  private void handleLocations(Location location) {
    if (location instanceof ResolvedAkkaLocation) {
      ResolvedAkkaLocation l = (ResolvedAkkaLocation) location;
      log.info("Got actorRef: " + l.getActorRef());
      tromboneHCD = l.getActorRef();
      supervisor.tell(Started, self());

    } else if (location instanceof ResolvedHttpLocation) {
      log.info("HTTP Service Damn it: " + location.connection());

    } else if (location instanceof ResolvedTcpLocation) {
      ResolvedTcpLocation t = (ResolvedTcpLocation) location;
      log.info("Received TCP Location: " + t.connection());

      // Verify that it is the event service
      if (location.connection().equals(IEventService.eventServiceConnection())) {
        log.info("Assembly received ES connection: " + t);
        // Setting var here!
        eventService = Optional.of(IEventService.getEventService(t.host(), t.port(), context()));
        log.info("Event Service at: " + eventService);
      }

      if (location.connection().equals(ITelemetryService.telemetryServiceConnection())) {
        log.info("Assembly received TS connection: " + t);
        // Setting var here!
        telemetryService = Optional.of(ITelemetryService.getTelemetryService(t.host(), t.port(), context()));
        log.info("Telemetry Service at: " + telemetryService);
      }

      if (location.connection().equals(IAlarmService.alarmServiceConnection(IAlarmService.defaultName))) {
        log.info("Assembly received AS connection: " + t);
        // Setting var here!
        alarmService = Optional.of(IAlarmService.getAlarmService(t.host(), t.port(), context()));
        log.info("Alarm Service at: " + alarmService);
      }

    } else if (location instanceof Unresolved) {
      log.info("Unresolved: " + location.connection());
      if (location.connection().componentId().equals(ac.hcdComponentId))
        tromboneHCD = badHCDReference;

    } else if (location instanceof UnTrackedLocation) {
      log.info("UnTracked: " + location.connection());

    } else {
      log.warning("Unknown connection: " + location.connection()); // XXX
    }
  }

  /**
   * This contains only commands that can be received during intialization
   *
   * @return Receive is a partial function
   */
  private PartialFunction<Object, BoxedUnit> initializingReceive() {
    return ReceiveBuilder.
      match(Location.class, this::handleLocations).
      matchEquals(Running, location -> {
        // When Running is received, transition to running Receive
        log.info("becoming runningReceive");
        context().become(runningReceive());
      }).
      matchAny(t -> log.warning("Unexpected message in TromboneAssembly:initializingReceive: " + t)).
      build();
  }

  private PartialFunction<Object, BoxedUnit> locationReceive() {
    return ReceiveBuilder.
      match(Location.class, this::handleLocations).
      build();
  }

  private PartialFunction<Object, BoxedUnit> runningReceive() {
    return locationReceive().orElse(controllerReceive()).orElse(lifecycleReceivePF()).orElse(unhandledPF());
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
        tromboneHCD.ifPresent(actorRef -> actorRef.tell(DoShutdown, self()));
        supervisor.tell(ShutdownComplete, self());
      }).
      match(Supervisor.LifecycleFailureInfo.class, t -> {
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
        executor.tell(new SequentialExecutor.StartTheSequence(commandHandler), self());
      }
    }
    return validations;
  }

  private ActorRef newExecutor(SetupConfigArg sca, Optional<ActorRef> commandOriginator) {
    return context().actorOf(SequentialExecutor.props(sca, commandOriginator));
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
