package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.ccs.DemandMatcher;
import csw.services.ccs.MultiStateMatcherActor;
import csw.services.ccs.SequentialExecutor.ExecuteOne;
import csw.services.ccs.StateMatcher;
import csw.util.config.BooleanItem;
import csw.util.config.DoubleItem;
import javacsw.services.ccs.JSequentialExecutor;
import javacsw.services.events.IEventService;
import javacsw.services.pkg.ILocationSubscriberClient;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static akka.pattern.PatternsCS.ask;
import static csw.examples.vsliceJava.assembly.TromboneStateActor.*;
import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static csw.services.ccs.CommandStatus.*;
import static csw.services.ccs.CommandStatus.Error;
import static csw.services.ccs.CommandStatus.Invalid;
import static csw.services.ccs.Validation.*;
import static csw.services.loc.LocationService.*;
import static csw.util.config.Configurations.ConfigKey;
import static csw.util.config.Configurations.SetupConfig;
import static csw.util.config.StateVariable.DemandState;
import static javacsw.services.ccs.JCommandStatus.Cancelled;
import static javacsw.services.ccs.JCommandStatus.Completed;
import static javacsw.util.config.JItems.*;
import static scala.compat.java8.OptionConverters.toJava;

/**
 * TMT Source Code: 9/21/16.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "WeakerAccess"})
class TromboneCommandHandler extends AbstractActor implements TromboneStateClient, ILocationSubscriberClient {
  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final Optional<ActorRef> allEventPublisher;

  @SuppressWarnings("FieldCanBeLocal")
  private final ActorRef badHCDReference;

  private ActorRef tromboneHCD;

  private boolean isHCDAvailable() {
    return !tromboneHCD.equals(badHCDReference);

  }

  private Optional<IEventService> badEventService = Optional.empty();
  private Optional<IEventService> eventService = badEventService;
//  private boolean isEventServiceAvailable() {
//    return eventService.isPresent();
//  }

  // Set the default evaluation for use with the follow command
  private DoubleItem setElevationItem;

  // The actor for managing the persistent assembly state as defined in the spec is here, it is passed to each command
  private final ActorRef tromboneStateActor;

  @SuppressWarnings("FieldCanBeLocal")
  private TromboneStateActor.TromboneState internalState = TromboneStateActor.defaultTromboneState;

  @Override
  public void setCurrentState(TromboneStateActor.TromboneState ts) {
    internalState = ts;
  }

  private TromboneStateActor.TromboneState currentState() {
    return internalState;
  }

  public TromboneCommandHandler(AssemblyContext ac, Optional<ActorRef> tromboneHCDIn, Optional<ActorRef> allEventPublisher) {
    this.ac = ac;
    badHCDReference = context().system().deadLetters();
    this.tromboneHCD = tromboneHCDIn.orElse(badHCDReference);
    tromboneStateActor = context().actorOf(TromboneStateActor.props());
    this.allEventPublisher = allEventPublisher;
    setElevationItem = AssemblyContext.naElevation(ac.calculationConfig.defaultInitialElevation);
    int moveCnt = 0;
    log.info("System  is: " + context().system());

    subscribeToLocationUpdates();
    context().system().eventStream().subscribe(self(), TromboneState.class);


    receive(noFollowReceive());
  }

  private void handleLocations(Location location) {
    if (location instanceof ResolvedAkkaLocation) {
      ResolvedAkkaLocation l = (ResolvedAkkaLocation) location;
      log.debug("CommandHandler receive an actorRef: " + l.getActorRef());
      tromboneHCD = l.getActorRef().orElse(badHCDReference);

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

    } else if (location instanceof Unresolved) {
      log.info("Unresolved: " + location.connection());
      if (location.connection().equals(IEventService.eventServiceConnection()))
        eventService = badEventService;
      if (location.connection().componentId().equals(ac.hcdComponentId))
        tromboneHCD = badHCDReference;

    } else {
      log.info("CommandHandler received some other location: " + location);
    }
  }

  private PartialFunction<Object, BoxedUnit> noFollowReceive() {
    return stateReceive().orElse(ReceiveBuilder.

      match(Location.class, this::handleLocations).

      match(ExecuteOne.class, t -> {

        SetupConfig sc = t.sc();
        Optional<ActorRef> commandOriginator = toJava(t.commandOriginator());
        ConfigKey configKey = sc.configKey();

        if (configKey.equals(ac.initCK)) {
          log.info("Init not fully implemented -- only sets state ready!");
          tromboneStateActor.tell(new SetState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)), self());
          commandOriginator.ifPresent(actorRef -> actorRef.tell(Completed, self()));

        } else if (configKey.equals(ac.datumCK)) {
          if (isHCDAvailable()) {
            log.info("Datums State: " + currentState());
            ActorRef datumActorRef = context().actorOf(DatumCommand.props(sc, tromboneHCD, currentState(), Optional.of(tromboneStateActor)));
            context().become(actorExecutingReceive(datumActorRef, commandOriginator));
            self().tell(JSequentialExecutor.CommandStart(), self());
          } else hcdNotAvailableResponse(commandOriginator);

        } else if (configKey.equals(ac.moveCK)) {
          if (isHCDAvailable()) {
            ActorRef moveActorRef = context().actorOf(MoveCommand.props(ac, sc, tromboneHCD, currentState(), Optional.of(tromboneStateActor)));
            context().become(actorExecutingReceive(moveActorRef, commandOriginator));
            self().tell(JSequentialExecutor.CommandStart(), self());
          } else hcdNotAvailableResponse(commandOriginator);

        } else if (configKey.equals(ac.positionCK)) {
          if (isHCDAvailable()) {
            ActorRef positionActorRef = context().actorOf(PositionCommand.props(ac, sc, tromboneHCD, currentState(), Optional.of(tromboneStateActor)));
            context().become(actorExecutingReceive(positionActorRef, commandOriginator));
            self().tell(JSequentialExecutor.CommandStart(), self());
          } else hcdNotAvailableResponse(commandOriginator);

        } else if (configKey.equals(ac.stopCK)) {
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new NoLongerValid(new WrongInternalStateIssue("Trombone assembly must be executing a command to use stop")), self()));

        } else if (configKey.equals(ac.setAngleCK)) {
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new NoLongerValid(new WrongInternalStateIssue("Trombone assembly must be following for setAngle")), self()));

        } else if (configKey.equals(ac.setElevationCK)) {
          // Setting the elevation state here for a future follow command
          setElevationItem = jitem(sc, AssemblyContext.naElevationKey);
          log.info("Setting elevation to: " + setElevationItem);
          // Note that units have already been verified here
          ActorRef setElevationActorRef = context().actorOf(SetElevationCommand.props(ac, sc, tromboneHCD, currentState(), Optional.of(tromboneStateActor)));
          context().become(actorExecutingReceive(setElevationActorRef, commandOriginator));
          self().tell(JSequentialExecutor.CommandStart(), self());

        } else if (configKey.equals(ac.followCK)) {
          if (cmd(currentState()).equals(cmdUninitialized)
            || (!move(currentState()).equals(moveIndexed) && !move(currentState()).equals(moveMoving))
            || !sodiumLayer(currentState())) {
            commandOriginator.ifPresent(actorRef ->
              actorRef.tell(new NoLongerValid(new WrongInternalStateIssue("Assembly state of "
                + cmd(currentState()) + "/" + move(currentState()) + "/" + sodiumLayer(currentState()) + " does not allow follow")), self()));
          } else {
            // No state set during follow
            // At this point, parameters have been checked so direct access is okay
            BooleanItem nssItem = jitem(sc, AssemblyContext.nssInUseKey);

            log.info("Set elevation is: " + setElevationItem);

            // The event publisher may be passed in
            Props props = FollowCommand.props(ac, setElevationItem, nssItem, Optional.of(tromboneHCD), allEventPublisher, eventService.get());
            // Follow command runs the trombone when following
            ActorRef followCommandActor = context().actorOf(props);
            log.info("Going to followReceive");
            context().become(followReceive(followCommandActor));
            // Note that this is where sodiumLayer is set allowing other commands that require this state
//            state(cmdContinuous, moveMoving, sodiumLayer(), jvalue(nssItem));
            tromboneStateActor.tell(new SetState(cmdContinuous, moveMoving, sodiumLayer(currentState()), jvalue(nssItem)), self());
            commandOriginator.ifPresent(actorRef -> actorRef.tell(Completed, self()));
          }

        } else {
          log.error("TromboneCommandHandler2:noFollowReceive received an unknown command: " + t + " from " + sender());
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new Invalid(new UnsupportedCommandInStateIssue("Trombone assembly does not support the command " +
              configKey.prefix() + " in the current state.")), self()));
        }
      }).
      matchAny(t ->
        log.warning("TromboneCommandHandler2:noFollowReceive received an unknown message: " + t + " from " + sender())).
      build());
  }

  private void hcdNotAvailableResponse(Optional<ActorRef> commandOriginator) {
    commandOriginator.ifPresent(actorRef -> actorRef.tell(
      new NoLongerValid(new RequiredHCDUnavailableIssue(ac.hcdComponentId.toString() + " is not available")), self()));
  }

  private PartialFunction<Object, BoxedUnit> followReceive(ActorRef followActor) {
    return stateReceive().orElse(ReceiveBuilder.
      match(ExecuteOne.class, t -> {
        SetupConfig sc = t.sc();
        Optional<ActorRef> commandOriginator = toJava(t.commandOriginator());
        ConfigKey configKey = sc.configKey();

        if (configKey.equals(ac.datumCK) || configKey.equals(ac.moveCK) || configKey.equals(ac.positionCK) || configKey.equals(ac.followCK) || configKey.equals(ac.setElevationCK)) {
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new Invalid(new WrongInternalStateIssue("Trombone assembly cannot be following for datum, move, position, setElevation, and follow")), self()));

        } else if (configKey.equals(ac.setAngleCK)) {
          // Unclear what to really do with state here
          // Everything else is the same
          tromboneStateActor.tell(new SetState(cmdBusy, move(currentState()), sodiumLayer(currentState()), nss(currentState())), self());

          // At this point, parameters have been checked so direct access is okay
          // Send the SetElevation to the follow actor
          DoubleItem zenithAngleItem = jitem(sc, AssemblyContext.zenithAngleKey);
          followActor.tell(new FollowActor.SetZenithAngle(zenithAngleItem), self());
          Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
          executeMatch(context(), idleMatcher(), tromboneHCD, commandOriginator, timeout, status -> {
            if (status == Completed)
              tromboneStateActor.tell(new SetState(cmdContinuous, move(currentState()), sodiumLayer(currentState()), nss(currentState())), self());
            else if (status instanceof Error)
              log.error("setElevation command failed with message: " + ((Error) status).message());
          });
        } else if (configKey.equals(ac.stopCK)) {
          // Stop the follower
          log.debug("Stop received while following");
          followActor.tell(new FollowCommand.StopFollowing(), self());
          tromboneStateActor.tell(new SetState(cmdReady, moveIndexed, sodiumLayer(currentState()), nss(currentState())), self());

          // Go back to no follow state
          context().become(noFollowReceive());
          commandOriginator.ifPresent(actorRef -> actorRef.tell(Completed, self()));
        }
      }).
      matchAny(t -> log.warning("TromboneCommandHandler:followReceive received an unknown message: " + t)).
      build());
  }

  private PartialFunction<Object, BoxedUnit> actorExecutingReceive(ActorRef currentCommand, Optional<ActorRef> commandOriginator) {
    Timeout timeout = new Timeout(5, TimeUnit.SECONDS);

    return stateReceive().orElse(ReceiveBuilder.
      matchEquals(JSequentialExecutor.CommandStart(), t -> {

        // Execute the command actor asynchronously, pass the command status back, kill the actor and go back to waiting
        ask(currentCommand, JSequentialExecutor.CommandStart(), timeout.duration().toMillis()).
          thenApply(reply -> {
            CommandStatus cs = (CommandStatus) reply;
            commandOriginator.ifPresent(actorRef -> actorRef.tell(cs, self()));
            currentCommand.tell(PoisonPill.getInstance(), self());
            context().become(noFollowReceive());
            return null;
          });
      }).

      match(SetupConfig.class, t -> t.configKey().equals(ac.stopCK), t -> {
        log.debug("actorExecutingReceive: Stop CK");
        closeDownMotionCommand(currentCommand, commandOriginator);
      }).

      match(ExecuteOne.class, t -> {
        log.debug("actorExecutingReceive: ExecuteOneStop");
        closeDownMotionCommand(currentCommand, commandOriginator);
      }).
      matchAny(t -> log.warning("TromboneCommandHandler:actorExecutingReceive received an unknown message: " + t)).
      build());
  }

  private void closeDownMotionCommand(ActorRef currentCommand, Optional<ActorRef> commandOriginator) {
    currentCommand.tell(JSequentialExecutor.StopCurrentCommand(), self());
    currentCommand.tell(PoisonPill.getInstance(), self());
    context().become(noFollowReceive());
    commandOriginator.ifPresent(actorRef -> actorRef.tell(Cancelled, self()));
  }

  // --- static defs ---

  public static Props props(AssemblyContext ac, Optional<ActorRef> tromboneHCDIn, Optional<ActorRef> allEventPublisher) {
    return Props.create(new Creator<TromboneCommandHandler>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneCommandHandler create() throws Exception {
        return new TromboneCommandHandler(ac, tromboneHCDIn, allEventPublisher);
      }
    });
  }

  static void executeMatch(ActorContext context, StateMatcher stateMatcher, ActorRef currentStateSource, Optional<ActorRef> replyTo,
                           Timeout timeout, Consumer<CommandStatus> codeBlock) {

    ActorRef matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout));

    ask(matcher, MultiStateMatcherActor.createStartMatch(stateMatcher), timeout).
      thenApply(reply -> {
        CommandStatus cmdStatus = (CommandStatus) reply;
        codeBlock.accept(cmdStatus);
        replyTo.ifPresent(actorRef -> actorRef.tell(cmdStatus, context.self()));
        return null;
      });
  }

  static DemandMatcher idleMatcher() {
    DemandState ds = jadd(new DemandState(axisStateCK.prefix()), jset(stateKey, TromboneHCD.AXIS_IDLE));
    return new DemandMatcher(ds, false);
  }

  static DemandMatcher posMatcher(int position) {
    DemandState ds = jadd(new DemandState(axisStateCK.prefix()),
      jset(stateKey, TromboneHCD.AXIS_IDLE),
      jset(positionKey, position));
    return new DemandMatcher(ds, false);
  }

}

