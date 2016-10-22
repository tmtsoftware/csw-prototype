package csw.examples.vsliceJava.assembly;

import akka.actor.*;
import akka.util.Timeout;
import csw.services.ccs.*;
import csw.services.ccs.CommandStatus2;
import csw.services.ccs.CommandStatus2.Invalid;
import csw.services.ccs.CommandStatus2.NoLongerValid;
import csw.services.ccs.SequentialExecution.SequentialExecutor.*;
import csw.services.ccs.StateMatchers.MultiStateMatcherActor.StartMatch;
import csw.services.ccs.StateMatchers.*;
import csw.services.ccs.Validation.*;
import csw.services.events.EventServiceSettings;
import csw.util.config.BooleanItem;
import csw.util.config.Configurations;
import csw.util.config.Configurations.*;
import csw.util.config.Configurations.SetupConfig;
import csw.util.config.DoubleItem;
import csw.util.config.StateVariable.DemandState;
import csw.util.config.UnitsOfMeasure.encoder;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import akka.util.Timeout;
import csw.examples.vsliceJava.hcd.TromboneHCD;
import csw.services.alarms.*;
import csw.util.config.JavaHelpers;
import javacsw.util.config.JConfigDSL;
import csw.util.config.StateVariable.CurrentState;

import javacsw.services.alarms.IAlarmService;
import javacsw.services.alarms.JAlarmService;
import javacsw.services.ccs.JCommandStatus2;
import javacsw.services.ccs.JHcdController;
import javacsw.util.config.JUnitsOfMeasure;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;
import java.util.concurrent.TimeUnit;
import scala.concurrent.duration.*;
import static javacsw.util.config.JItems.*;
import static javacsw.util.config.JConfigDSL.*;

import java.util.Optional;
import javacsw.services.ccs.JSequentialExecution;

import static akka.pattern.PatternsCS.*;

import static csw.examples.vsliceJava.hcd.TromboneHCD.*;
import static javacsw.services.ccs.JCommandStatus2.Cancelled;
import static javacsw.services.ccs.JCommandStatus2.Completed;
import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JItems.jvalue;
import static scala.compat.java8.OptionConverters.toJava;

/**
 * TMT Source Code: 9/21/16.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class TromboneCommandHandler extends TromboneStateHandler {
  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final AssemblyContext ac;
  private final ActorRef tromboneHCDIn;
  private final Optional<ActorRef> allEventPublisher;

  private int moveCnt;
  // Diags
  private final ActorRef tromboneHCD;

  private final EventServiceSettings testEventServiceSettings;

  public TromboneCommandHandler(AssemblyContext ac, ActorRef tromboneHCDIn, Optional<ActorRef> allEventPublisher) {
    this.ac = ac;
    this.tromboneHCDIn = tromboneHCDIn;
    this.allEventPublisher = allEventPublisher;

    moveCnt = 0;
    tromboneHCD= tromboneHCDIn;
    testEventServiceSettings = new EventServiceSettings("localhost", 7777);

//    receive(ReceiveBuilder.
//      matchAny(t -> log.warning("Unknown message received: " + t)).
//      build());

    context().become(noFollowReceive());
  }


  private PartialFunction<Object, BoxedUnit> noFollowReceive() {
    return ReceiveBuilder.
      match(ExecuteOne.class, t -> {
        SetupConfig sc = t.sc();
        Optional<ActorRef> commandOriginator = toJava(t.commandOriginator());
        ConfigKey configKey = sc.configKey();
        if (configKey.equals(ac.initCK)) {
          log.info("Init not yet implemented");
        } else if (configKey.equals(ac.datumCK)) {
          ActorRef datumActorRef = DatumCommand(sc, tromboneHCD);
          context().become(actorExecutingReceive(datumActorRef, commandOriginator));
          self().tell(JSequentialExecution.CommandStart(), self());
        } else if (configKey.equals(ac.moveCK)) {
          ActorRef moveActorRef = MoveCommand(sc, tromboneHCD);
          context().become(actorExecutingReceive(moveActorRef, commandOriginator));
          self().tell(JSequentialExecution.CommandStart(), self());
        } else if (configKey.equals(ac.positionCK)) {
          ActorRef positionActorRef = PositionCommand(sc, tromboneHCD);
          context().become(actorExecutingReceive(positionActorRef, commandOriginator));
          self().tell(JSequentialExecution.CommandStart(), self());
        } else if (configKey.equals(ac.stopCK)) {
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new Invalid(new WrongInternalStateIssue("Trombone assembly must be executing a command to use stop")), self()));
        } else if (configKey.equals(ac.setAngleCK)) {
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new Invalid(new WrongInternalStateIssue("Trombone assembly must be following for setAngle")), self()));
        } else if (configKey.equals(ac.setElevationCK)) {
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new Invalid(new WrongInternalStateIssue("Trombone assembly must be following for setElevation")), self()));
        } else if (configKey.equals(ac.followCK)) {
          if (cmd().equals(cmdUninitialized) || (!move().equals(moveIndexed) && !move().equals(moveMoving)) || !sodiumLayer()) {
            commandOriginator.ifPresent(actorRef ->
              actorRef.tell(new NoLongerValid(new WrongInternalStateIssue("Assembly state of $cmd/$move does not allow follow")), self()));
          } else {
            // No state set during follow
            // At this point, parameters have been checked so direct access is okay
            BooleanItem nssItem = JavaHelpers.jvalue(sc, ac.nssInUseKey);

            // The event publisher may be passed in
            Props props = FollowCommand.props(ac, nssItem, Optional.of(tromboneHCD), allEventPublisher, Optional.of(testEventServiceSettings));
            // Follow command runs the trombone when following
            ActorRef followCommandActor = context().actorOf(props);
            context().become(followReceive(followCommandActor));
            // Note that this is where sodiumLayer is set allowing other commands that require this state
            state(cmdContinuous, moveMoving, sodiumLayer(), jvalue(nssItem));
            commandOriginator.ifPresent(actorRef -> actorRef.tell(Completed, self()));
          }
        } else {
          log.error("TromboneCommandHandler2:noFollowReceive received an unknown command: " + t);
          commandOriginator.ifPresent(actorRef ->
            actorRef.tell(new Invalid(new UnsupportedCommandInStateIssue("Trombone assembly does not support the command "+
            configKey.prefix() + " in the current state.")), self()));
        }
      }).
      matchAny(t -> log.warning("TromboneCommandHandler2:noFollowReceive received an unknown message: " + t)).
      build();
  }

  private PartialFunction<Object, BoxedUnit> followReceive(ActorRef followActor) {
    return ReceiveBuilder.
      match(ExecuteOne.class, t -> {
          SetupConfig sc = t.sc();
          Optional<ActorRef> commandOriginator = toJava(t.commandOriginator());
          ConfigKey configKey = sc.configKey();
          if (configKey.equals(ac.datumCK) || configKey.equals(ac.moveCK) || configKey.equals(ac.positionCK) || configKey.equals(ac.followCK)) {
            commandOriginator.ifPresent(actorRef ->
              actorRef.tell(new Invalid(new WrongInternalStateIssue("Trombone assembly cannot be following for datum, move, position, and follow")), self()));
          } else if (configKey.equals(ac.setElevationCK)) {
            // Unclear what to really do with state here
            // Everything else is the same
            state(cmdBusy, move(), sodiumLayer(), nss());

            // At this point, parameters have been checked so direct access is okay
            // Send the SetElevation to the follow actor
            DoubleItem elevationItem = JavaHelpers.jvalue(sc, ac.naElevationKey);
            followActor.tell(new FollowActor.SetElevation(elevationItem), self());
            executeMatch(context(), idleMatcher, tromboneHCD, commandOriginator) {
              case Completed =>
                state(cmdContinuous, move(), sodiumLayer(), nss());
              case Error(message) =>
                log.error("setElevation command failed with message: " + message);
            }
          } else if (configKey.equals(ac.setAngleCK)) {
            // Unclear what to really do with state here
            // Everything else is the same
            state(cmdBusy, move(), sodiumLayer(), nss());

            // At this point, parameters have been checked so direct access is okay
            // Send the SetAngle to the follow actor
            DoubleItem zenithAngleItem = JavaHelpers.jvalue(sc, ac.zenithAngleKey);
            followActor.tell(new FollowActor.SetZenithAngle(zenithAngleItem), self());
            executeMatch(context, idleMatcher, tromboneHCD, commandOriginator) {
              case Completed =>
                state(cmdContinuous, move(), sodiumLayer(), nss());
              case Error(message) =>
                log.error("setElevation command failed with message: " + message);
            }
          } else if (configKey.equals(ac.stopCK)) {
            // Stop the follower
            log.info("Just received the stop");
            followActor.tell(StopFollowing, self());
            state(cmdReady, move(), sodiumLayer(), nss());
            // Go back to no follow state
            context().become(noFollowReceive());
            commandOriginator.ifPresent(actorRef -> actorRef.tell(Completed, self()));
        }

      }).
      matchAny(t -> log.warning("TromboneCommandHandler2:noFollowReceive received an unknown message: " + t)).
      build();
  }

  private PartialFunction<Object, BoxedUnit> actorExecutingReceive(ActorRef currentCommand, Optional<ActorRef> commandOriginator) {
    Timeout timeout = new Timeout(5, TimeUnit.SECONDS);

    return ReceiveBuilder.
      matchEquals(JSequentialExecution.CommandStart(), t -> {

        // Execute the command actor asynchronously, pass the command status back, kill the actor and go back to waiting
        ask(currentCommand, JSequentialExecution.CommandStart(), timeout.duration().toMillis()).
          thenApply(reply -> {
            CommandStatus2 cs = (CommandStatus2) reply;
            commandOriginator.ifPresent(actorRef -> actorRef.tell(cs, self()));
            currentCommand.tell(PoisonPill.getInstance(), self());
            context().become(noFollowReceive());
            return null;
          });
      }).
      matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
        // This sends the Stop sc to the HCD
        log.debug("actorExecutingReceive STOP STOP");
        closeDownMotionCommand(currentCommand, commandOriginator);
      }).
      match(SetupConfig.class, t -> {
        // This sends the Stop sc to the HCD
        log.debug("actorExecutingReceive: Stop CK");
        closeDownMotionCommand(currentCommand, commandOriginator);
      }).
      matchAny(t -> log.warning("TromboneCommandHandler2:actorExecutingReceive received an unknown message: " + t)).
      build();
  }

  private void closeDownMotionCommand(ActorRef currentCommand, Optional<ActorRef> commandOriginator) {
    currentCommand.tell(JSequentialExecution.StopCurrentCommand(), self());
    currentCommand.tell(PoisonPill.getInstance(), self());
    context().become(noFollowReceive());
    commandOriginator.ifPresent(actorRef -> actorRef.tell(Cancelled, self()));
  }

  // XXX allan FIXME: Using a nonstatic inner actor class here. Is that OK? (sharing state with outer actor class)
  private class DatumCommandActor extends AbstractActor {

    private final SetupConfig sc;
    private final ActorRef tromboneHCD;
    private final TromboneState ts;

    private DatumCommandActor(SetupConfig sc, ActorRef tromboneHCD, TromboneState ts) {
      this.sc = sc;
      this.tromboneHCD = tromboneHCD;
      this.ts = ts;

      // Not using stateReceive since no state updates are needed here only writes
      receive(ReceiveBuilder.
        matchEquals(JSequentialExecution.CommandStart()), t -> {
          if (cmd().equals(cmdUninitialized)) {
            sender().tell(new NoLongerValid(new WrongInternalStateIssue("Assembly state of "
              + cmd() + "/" + move() + " does not allow datum"));
          } else {
            log.info("In Start: " + tromboneState);
            ActorRef mySender = sender();
            state(cmdBusy, moveIndexing, sodiumLayer(), nss());
            tromboneHCD.tell(new HcdController.Submit(new SetupConfig(axisDatumCK.prefix())), self());
            executeMatch(context(), idleMatcher, tromboneHCD, Optional.of(mySender)) {
              case Completed =>
                state(cmdReady, moveIndexed, false, false);
              case Error(message) =>
                log.error("Error: " + message);
            }
          }
        }).
        matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
          log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>  DATUM STOP STOP");
          tromboneHCD.tell(new HcdController.Submit(cancelSC), self());
        }).
        matchAny(t -> log.warning("Unknown message received: " + t)).
        build();
    }
  }

  // XXX use a props() method?
  ActorRef DatumCommand(SetupConfig sc, ActorRef tromboneHCD) {
    // XXX FIXME allan: This will fail since it is an inner class!
    return context().actorOf(Props.create(DatumCommandActor.class, sc, tromboneHCD, tromboneState));
  }

  // XXX TODO FIXME nonstatic static inner class actor
  class MoveCommandActor extends AbstractActor {
    private final AssemblyContext.TromboneControlConfig controlConfig;
    private final SetupConfig sc;
    private final ActorRef tromboneHCD;

    private MoveCommandActor(AssemblyContext.TromboneControlConfig controlConfig, SetupConfig sc, ActorRef tromboneHCD) {
      this.controlConfig = controlConfig;
      this.sc = sc;
      this.tromboneHCD = tromboneHCD;

      receive(ReceiveBuilder.
        matchEquals(JSequentialExecution.CommandStart(), t -> {
          // Move moves the trombone state in mm but not encoder units
          if (cmd().equals(cmdUninitialized) || (!move().equals(moveIndexed) && !move().equals(moveMoving))) {
            sender().tell(new NoLongerValid(new WrongInternalStateIssue("Assembly state of $cmd/$move does not allow move")), self());
          } else {
            ActorRef mySender = sender();
            DoubleItem stagePosition = JavaHelpers.jvalue(sc, ac.stagePositionKey);

            // Convert to encoder units from mm
            int encoderPosition = Algorithms.stagePositionToEncoder(controlConfig, jvalue(stagePosition));

            log.info("Setting trombone axis to: " + encoderPosition);

            DemandMatcher stateMatcher = posMatcher(encoderPosition);
            // Position key is encoder units
            SetupConfig scOut =  jadd(sc(axisMoveCK.prefix()),
              jset(positionKey, encoderPosition).withUnits(JUnitsOfMeasure.encoder));

            state(cmdBusy, moveMoving, sodiumLayer(), nss());
            tromboneHCD.tell(new HcdController.Submit(scOut), self());
            executeMatch(context(), stateMatcher, tromboneHCD, Optional.of(mySender)) {
              case Completed =>
                state(cmdReady, moveIndexed, sodiumLayer(), nss());
                moveCnt += 1;
                log.info("Done with move at: " + moveCnt);
              case Error(message) =>
                log.error("Move command failed with message: " + message);
            }
          }
        }).
        matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
          log.info("Move command -- STOP");
          tromboneHCD.tell(new HcdController.Submit(cancelSC), self());
        }).
        matchAny(t -> log.warning("Unknown message received: " + t)).
        build());
    }
  }

  ActorRef MoveCommand(SetupConfig sc, ActorRef tromboneHCD) {
    // XXX FIXME allan: This will fail since it is an inner class!
    return context().actorOf(Props.create(MoveCommandActor.class, ac.controlConfig, sc, tromboneHCD));
  }

  private class PositionCommandActor extends AbstractActor {

    private final AssemblyContext.TromboneControlConfig controlConfig;
    private final SetupConfig sc;
    private final ActorRef tromboneHCD;

    private PositionCommandActor(AssemblyContext.TromboneControlConfig controlConfig, SetupConfig sc, ActorRef tromboneHCD) {
      this.controlConfig = controlConfig;
      this.sc = sc;
      this.tromboneHCD = tromboneHCD;

      receive(ReceiveBuilder.
        matchEquals(JSequentialExecution.CommandStart(), t -> {
          if (cmd().equals(cmdUninitialized) || (!move().equals(moveIndexed) && !move().equals(moveMoving))) {
            sender().tell(new NoLongerValid(new WrongInternalStateIssue("Assembly state of "
              + cmd() + "/" + move() + " does not allow motion"));
          } else {
            ActorRef mySender = sender();

            // Note that units have already been verified here
            val rangeDistance = sc(ac.naRangeDistanceKey)

            // Convert elevation to
            // Convert to encoder units from mm
            val stagePosition = Algorithms.rangeDistanceToStagePosition(rangeDistance.head)
            val encoderPosition = Algorithms.stagePositionToEncoder(controlConfig, stagePosition)

            log.info(s"Using rangeDistance: ${rangeDistance.head} to get stagePosition: $stagePosition to encoder: $encoderPosition")

            val stateMatcher = posMatcher(encoderPosition)
            // Position key is encoder units
            SetupConfig scOut = jset(new SetupConfig(axisMoveCK.prefix()), positionKey, encoderPosition).withUnits(JUnitsOfMeasure.encoder);
            state(cmdBusy, moveMoving, sodiumLayer(), nss());
            tromboneHCD.tell(new HcdController.Submit(scOut), self());

            executeMatch(context, stateMatcher, tromboneHCD, Some(mySender)) {
              case Completed =>
                state(cmd = cmdReady, move = moveIndexed)
                moveCnt += 1
                log.info(s"Done with position at: $moveCnt")
              case Error(message) =>
                log.error(s"Position command failed with message: $message")
            }
          }
        }).
        matchEquals(JSequentialExecution.StopCurrentCommand(), t -> {
          log.info("Move command -- STOP")
          tromboneHCD ! HcdController.Submit(cancelSC)
        }).
        matchAny(t -> log.warning("Unknown message received: " + t)).
        build());
    }

    override def postStop() {
      unsubscribeState();
    }
  }

  def PositionCommand(sc: SetupConfig, tromboneHCD: ActorRef): ActorRef = {
    context.actorOf(Props(new PositionCommandActor(ac.controlConfig, sc, tromboneHCD)))
  }

  def executeMatch(context: ActorContext, stateMatcher: StateMatcher, currentStateSource: ActorRef, replyTo: Option[ActorRef] = None,
                   timeout: Timeout = Timeout(5.seconds))(codeBlock: PartialFunction[CommandStatus2, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    for {
      cmdStatus <- (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus2]
    } {
      codeBlock(cmdStatus)
      replyTo.ifPresent(actorRef -> actorRef.tell( cmdStatus)
    }
  }

  def idleMatcher: DemandMatcher = DemandMatcher(DemandState(axisStateCK).add(stateKey -> TromboneHCD.AXIS_IDLE))

  def posMatcher(position: Int): DemandMatcher =
    DemandMatcher(DemandState(axisStateCK).madd(stateKey -> TromboneHCD.AXIS_IDLE, positionKey -> position))

  def executeMatch2(context: ActorContext, stateMatcher: StateMatcher, currentStateSource: ActorRef, replyTo: Option[ActorRef] = None,
                    timeout: Timeout = Timeout(5.seconds))(codeBlock: PartialFunction[CommandStatus2, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    val cmdStatusF: Future[CommandStatus2] = (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus2]
    cmdStatusF.onComplete {
      case Success(cmdStatus) =>
        codeBlock(cmdStatus)
      case Failure(e) =>
        log.error("Failure of matcher most likely due to timeout")
    }
  }
}

object TromboneCommandHandler {

  def props(assemblyContext: AssemblyContext, tromboneHCDIn: ActorRef, allEventPublisher: Option[ActorRef]) =
    Props(new TromboneCommandHandler(assemblyContext, tromboneHCDIn, allEventPublisher))

}