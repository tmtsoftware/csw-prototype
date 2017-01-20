package csw.examples.vsliceJava.assembly;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import com.typesafe.config.Config;
import csw.services.ccs.AssemblyMessages;
import csw.services.ccs.SequentialExecutor;
import csw.services.ccs.Validation;
import csw.services.loc.LocationService.*;
import csw.services.loc.LocationSubscriberActor;
import csw.services.pkg.Component;
import csw.services.pkg.Supervisor;
import javacsw.services.alarms.IAlarmService;
import javacsw.services.ccs.JAssemblyMessages;
import javacsw.services.cs.akka.JConfigServiceClient;
import javacsw.services.events.IEventService;
import javacsw.services.events.ITelemetryService;
import javacsw.services.loc.JLocationSubscriberActor;
import javacsw.services.ccs.JAssemblyController;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static csw.examples.vsliceJava.assembly.AssemblyContext.TromboneCalculationConfig;
import static csw.examples.vsliceJava.assembly.AssemblyContext.TromboneControlConfig;
import static csw.util.config.Configurations.SetupConfigArg;
import static javacsw.services.pkg.JSupervisor.*;

/**
 * Top Level Actor for Trombone Assembly
 *
 * TromboneAssembly starts up the component doing the following:
 * creating all needed actors,
 * handling initialization,
 * participating in lifecycle with Supervisor,
 * handles locations for distribution throughout component
 * receives comamnds and forwards them to the CommandHandler by extending the AssemblyController
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "WeakerAccess"})
public class TromboneAssembly extends JAssemblyController {

  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final ActorRef supervisor;
  private final AssemblyContext ac;
  private ActorRef commandHandler;

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

  private ActorRef diagPublsher;

  public TromboneAssembly(Component.AssemblyInfo info, ActorRef supervisor) {
    super(info);
    this.supervisor = supervisor;

    ac = initialize(info);

    // Initial receive - start with initial values
    receive(initializingReceive());
  }

  private AssemblyContext initialize(Component.AssemblyInfo info) {
    try {
      // Get the assembly configuration from the config service or resource file
      TromboneConfigs configs = getAssemblyConfigs();
      AssemblyContext assemblyContext = new AssemblyContext(info, configs.calculationConfig, configs.controlConfig);

      // Start tracking the components we command
      log.info("Connections: " + info.connections());

      ActorRef trackerSubscriber = context().actorOf(LocationSubscriberActor.props());
      trackerSubscriber.tell(JLocationSubscriberActor.Subscribe, self());

      // This actor handles all telemetry and system event publishing
      ActorRef eventPublisher = context().actorOf(TrombonePublisher.props(assemblyContext, Optional.empty(), Optional.empty()));

      // Setup command handler for assembly - note that CommandHandler connects directly to tromboneHCD here, not state receiver
      commandHandler = context().actorOf(TromboneCommandHandler.props(assemblyContext, tromboneHCD, Optional.of(eventPublisher)));

      // This sets up the diagnostic data publisher
      diagPublsher = context().actorOf(DiagPublisher.props(assemblyContext, tromboneHCD, Optional.of(eventPublisher)));

      // This tracks the HCD
      LocationSubscriberActor.trackConnections(info.connections(), trackerSubscriber);
      // This tracks required services
      LocationSubscriberActor.trackConnection(IEventService.eventServiceConnection(), trackerSubscriber);
      LocationSubscriberActor.trackConnection(ITelemetryService.telemetryServiceConnection(), trackerSubscriber);
      LocationSubscriberActor.trackConnection(IAlarmService.alarmServiceConnection(), trackerSubscriber);

      supervisor.tell(Initialized, self());
      return assemblyContext;

    } catch(Exception ex) {
      supervisor.tell(new Supervisor.InitializeFailure(ex.getMessage()), self());
      return null;
    }
  }


  /**
   * This contains only commands that can be received during intialization
   *
   * @return Receive is a partial function
   */
  private PartialFunction<Object, BoxedUnit> initializingReceive() {
    return locationReceive().orElse(ReceiveBuilder.
      matchEquals(Running, location -> {
        // When Running is received, transition to running Receive
        log.info("becoming runningReceive");
        context().become(runningReceive());
      }).
      matchAny(t -> log.warning("Unexpected message in TromboneAssembly:initializingReceive: " + t)).
      build());
  }

  private PartialFunction<Object, BoxedUnit> locationReceive() {
    return ReceiveBuilder.
      match(Location.class, location ->    {
          if (location instanceof ResolvedAkkaLocation) {
            ResolvedAkkaLocation l = (ResolvedAkkaLocation) location;
            log.info("Got actorRef: " + l.getActorRef());
            tromboneHCD = l.getActorRef();
            // When the HCD is located, Started is sent to Supervisor
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
              eventService = Optional.of(IEventService.getEventService(t.host(), t.port(), context().system()));
              log.info("Event Service at: " + eventService);
            }

            if (location.connection().equals(ITelemetryService.telemetryServiceConnection())) {
              log.info("Assembly received TS connection: " + t);
              // Setting var here!
              telemetryService = Optional.of(ITelemetryService.getTelemetryService(t.host(), t.port(), context().system()));
              log.info("Telemetry Service at: " + telemetryService);
            }

            if (location.connection().equals(IAlarmService.alarmServiceConnection(IAlarmService.defaultName))) {
              log.info("Assembly received AS connection: " + t);
              // Setting var here!
              alarmService = Optional.of(IAlarmService.getAlarmService(t.host(), t.port(), context().system()));
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
      ).
      build();
  }

  // Receive partial function used when in Running state
  private PartialFunction<Object, BoxedUnit> runningReceive() {
    return locationReceive().orElse(diagReceive()).orElse(controllerReceive()).orElse(lifecycleReceivePF()).orElse(unhandledPF());
  }

  // Receive partial function for handling the diagnostic commands
  private PartialFunction<Object, BoxedUnit> diagReceive() {
    return ReceiveBuilder.
      match(AssemblyMessages.DiagnosticMode.class, t -> {
        log.debug("Received diagnostic mode: " + t.hint());
        diagPublsher.tell(new DiagPublisher.DiagnosticState(), self());
      }).
      matchEquals(JAssemblyMessages.OperationsMode, t -> {
        log.debug("Received operations mode");
        diagPublsher.tell(new DiagPublisher.OperationsState(), self());
      }).
      build();
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

  // Catchall unhandled message receive
  private PartialFunction<Object, BoxedUnit> unhandledPF() {
    return ReceiveBuilder.
      matchAny(t -> log.warning("Unexpected message in TromboneAssembly:unhandledPF: " + t)).
      build();
  }

  /**
   * Function that overrides AssemblyController setup processes incoming SetupConfigArg messages
   * @param sca received SetupConfgiArg
   * @param commandOriginator the sender of the command
   * @return a validation object that indicates if the received config is valid
   */
  @Override
  public List<Validation.Validation> setup(SetupConfigArg sca, Optional<ActorRef> commandOriginator) {
    // Returns validations for all
    List<Validation.Validation> validations = validateSequenceConfigArg(sca);
    if (Validation.isAllValid(validations)) {
      // Create a SequentialExecutor to process all SetupConfigs
      ActorRef executor = newExecutor(commandHandler, sca, commandOriginator);
    }
    return validations;
  }

  /**
   * Validates a received config arg and returns the first
   */
  private List<Validation.Validation> validateSequenceConfigArg(SetupConfigArg sca) {
    // Are all of the configs really for us and correctly formatted, etc?
    return ConfigValidation.validateTromboneSetupConfigArg(sca, ac);
  }

  // Convenience method to create a new SequentialExecutor
  private ActorRef newExecutor(ActorRef commandHandler, SetupConfigArg sca, Optional<ActorRef> commandOriginator) {
    return context().actorOf(SequentialExecutor.props(commandHandler, sca, commandOriginator));
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
  private TromboneConfigs getAssemblyConfigs() throws Exception {
    // Get the trombone config file from the config service, or use the given resource file if that doesn't work
    Timeout timeout = new Timeout(3, TimeUnit.SECONDS);
    Optional<Config> configOpt = JConfigServiceClient.getConfigFromConfigService(tromboneConfigFile,
      Optional.empty(), Optional.of(resource), context().system(), timeout).get();
    if (configOpt.isPresent())
      return new TromboneConfigs(new TromboneCalculationConfig(configOpt.get()),
        new TromboneControlConfig(configOpt.get()));
    throw new RuntimeException("Failed to get from config service: " + tromboneConfigFile);
  }

  // --- Static defs ---

  public static File tromboneConfigFile = new File("trombone/tromboneAssembly.conf");
  public static File resource = new File("tromboneAssembly.conf");


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

}
