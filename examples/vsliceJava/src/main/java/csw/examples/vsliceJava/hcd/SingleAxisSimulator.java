package csw.examples.vsliceJava.hcd;


import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.services.ts.AbstractTimeServiceScheduler;
import csw.services.ts.TimeService;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Optional;

import static csw.examples.vsliceJava.hcd.SingleAxisSimulator.AxisState.*;

/**
 * This class provides a simulator of a single axis device for the purpose of testing TMT HCDs and Assemblies.
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "CodeBlock2Expr", "WeakerAccess"})
public class SingleAxisSimulator extends AbstractTimeServiceScheduler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  final AxisConfig axisConfig;
  private final Optional<ActorRef> replyTo;

  // The following are state information for the axis. These values are updated while the axis runs
  // This is safe because there is no way to change the variables other than within this actor
  // When created, the current is set to the start current
  int current;
  boolean inLowLimit = false;
  boolean inHighLimit = false;
  boolean inHome = false;
  AxisState axisState = AXIS_IDLE;

  // Statistics for status
  int initCount = 0;
  // Number of init requests
  int moveCount = 0;
  // Number of move requests
  int homeCount = 0;
  // Number of home requests
  int limitCount = 0;
  // Number of times in a limit
  int successCount = 0;
  // Number of successful requests
  int failureCount = 0;
  // Number of failed requests
  int cancelCount = 0; // Number of times a move has been cancelled


  /**
   * Constructor
   *
   * @param axisConfig an AxisConfig object that contains a description of the axis
   * @param replyTo    an actor that will be updated with information while the axis executes
   */
  private SingleAxisSimulator(AxisConfig axisConfig, Optional<ActorRef> replyTo) {
    this.axisConfig = axisConfig;
    this.replyTo = replyTo;
    current = axisConfig.startPosition;

    // Check that the home position is not in a limit area - with this check it is not necessary to check for limits after homing
    if (axisConfig.home <= axisConfig.lowUser)
      throw new AssertionError("home position must be greater than lowUser value: " + axisConfig.lowUser);
    if (axisConfig.home >= axisConfig.highUser)
      throw new AssertionError("home position must be less than highUser value: " + axisConfig.highUser);

    receive(idleReceive());
  }

  // Short-cut to forward a messaage to the optional replyTo actor
  void update(Optional<ActorRef> replyTo, Object msg) {
    replyTo.ifPresent(actorRef -> actorRef.tell(msg, self()));
  }

  // Actor state while working (after receiving the initial PublisherInfo message)
  PartialFunction<Object, BoxedUnit> idleReceive() {
    return ReceiveBuilder

      .matchEquals(InitialState.instance, e -> sender().tell(getState(), self()))

      .matchEquals(Datum.instance, e -> {
        axisState = AXIS_MOVING;
        update(replyTo, AxisStarted.instance);
        // Takes some time and increments the current
        scheduleOnce(TimeService.localTimeNow().plusSeconds(1), self(), DatumComplete.instance);
        // Stats
        initCount++;
        moveCount++;
      })

      .matchEquals(DatumComplete.instance, e -> {
        // Set limits
        axisState = AXIS_IDLE;
        // Power on causes motion of one unit!
        current++;
        checkLimits();
        // Stats
        successCount++;
        // Send Update
        update(replyTo, getState());
      })

      .match(GetStatistics.class, e -> {
        sender().tell(
          new AxisStatistics(axisConfig.axisName, initCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount),
          self());
      })

      .matchEquals(PublishAxisUpdate.instance, e -> {
        update(replyTo, getState());
      })

      .matchEquals(Home.instance, e -> {
        axisState = AXIS_MOVING;
        log.debug("AxisHome: " + axisState);
        update(replyTo, AxisStarted.instance);
        Props props = MotionWorker.props(current, axisConfig.home, 100, self(), false);
        ActorRef mw = context().actorOf(props, "homeWorker");
        context().become(homeReceive(mw));
        mw.tell(MotionWorker.Start.instance, self());
        // Stats
        moveCount++;
      })
      .match(HomeComplete.class, e -> {
        axisState = AXIS_IDLE;
        current = e.position;
        // Set limits
        checkLimits();
        if (inHome) homeCount += 1;
        // Stats
        successCount++;
        // Send Update
        update(replyTo, getState());
      })
      .match(Move.class, e -> {
        log.debug("Move: " + e.position);
        axisState = AXIS_MOVING;
        update(replyTo, AxisStarted.instance);
        int clampedTargetPosition = SingleAxisSimulator.limitMove(axisConfig, e.position);
        // The 200 ms here is the time for one step, so a 10 step move takes 2 seconds
        Props props = MotionWorker.props(current, clampedTargetPosition, axisConfig.stepDelayMS, self(), e.diagFlag);
        ActorRef mw = context().actorOf(props, "moveWorker-" + System.currentTimeMillis());
        context().become(moveReceive(mw));
        mw.tell(MotionWorker.Start.instance, self());
        // Stats
        moveCount++;
      })
      .match(MoveComplete.class, e -> {
        log.debug("Move Complete");
        axisState = AXIS_IDLE;
        current = e.position;
        // Set limits
        checkLimits();
        // Do the count of limits
        if (inHighLimit || inLowLimit) limitCount += 1;
        // Stats
        successCount++;
        // Send Update
        update(replyTo, getState());
      })
      .match(CancelMove.class, e -> {
        log.debug("Received Cancel Move while idle :-(");
        // Stats
        cancelCount += 1;
      })
      .matchAny(x -> log.warning("Unexpected message in idleReceive: " + x))
      .build();
  }

  // This receive is used when executing a Home command
  PartialFunction<Object, BoxedUnit> homeReceive(ActorRef worker) {
    return ReceiveBuilder
      .match(MotionWorker.Start.class, e -> {
        log.debug("Home Start");
      })
      .match(MotionWorker.Tick.class, e -> {
        current = e.current;
        // Send Update
        update(replyTo, getState());
      })
      .match(MotionWorker.End.class, e -> {
        context().become(idleReceive());
        self().tell(new HomeComplete(e.finalpos), self());
      })
      .matchAny(x -> log.warning("Unexpected message in homeReceive: " + x))
      .build();
  }

  PartialFunction<Object, BoxedUnit> moveReceive(ActorRef worker) {
    return ReceiveBuilder
      .match(MotionWorker.Start.class, e -> log.debug("Move Start"))
      .match(CancelMove.class, e -> {
        worker.tell(MotionWorker.Cancel.instance, self());
        // Stats
        cancelCount++;
      })
      .match(Move.class, e -> {
        // When this is received, we update the final position while a motion is happening
        worker.tell(new MotionWorker.MoveUpdate(e.position), self());
      })
      .match(MotionWorker.Tick.class, e -> {
        current = e.current;
        log.debug("Move Update");
        // Set limits - this was a bug - need to do this after every step
        checkLimits();
        // Send Update to caller
        update(replyTo, getState());
      })
      .match(MotionWorker.End.class, e -> {
        log.debug("Move End");
        context().become(idleReceive());
        self().tell(new MoveComplete(e.finalpos), self());
      })
      .matchAny(x -> log.warning("Unexpected message in moveReceive: " + x))
      .build();
  }


  void checkLimits() {
    inHighLimit = isHighLimit(axisConfig, current);
    inLowLimit = isLowLimit(axisConfig, current);
    inHome = isHomed(axisConfig, current);
  }

  AxisUpdate getState() {
    return new AxisUpdate(axisConfig.axisName, axisState, current, inLowLimit, inHighLimit, inHome);
  }

  // --- Static definitions ---

  /**
   * Used to create the actor
   *
   * @param axisConfig an AxisConfig object that contains a description of the axis
   * @param replyTo    an actor that will be updated with information while the axis executes
   * @return the props to use to create the actor
   */
  public static Props props(final AxisConfig axisConfig, final Optional<ActorRef> replyTo) {
    return Props.create(new Creator<SingleAxisSimulator>() {
      private static final long serialVersionUID = 1L;

      @Override
      public SingleAxisSimulator create() throws Exception {
        return new SingleAxisSimulator(axisConfig, replyTo);
      }
    });
  }

  public enum AxisState {
    AXIS_IDLE,
    AXIS_MOVING,
    AXIS_ERROR,
  }

  public interface AxisRequest {
  }

  public static class Home implements AxisRequest {
    public static final Home instance = new Home();

    private Home() {
    }
  }

  public static class Datum implements AxisRequest {
    public static final Datum instance = new Datum();

    private Datum() {
    }
  }

  public static class Move implements AxisRequest {
    int position;
    boolean diagFlag;

    public Move(int position, boolean diagFlag) {
      this.position = position;
      this.diagFlag = diagFlag;
    }

    public Move(int position) {
      this.position = position;
      this.diagFlag = false;
    }
  }

  @SuppressWarnings("unused")
  public static class CancelMove implements AxisRequest {
    public static final CancelMove instance = new CancelMove();

    private CancelMove() {
    }
  }

  @SuppressWarnings("unused")
  public static class GetStatistics implements AxisRequest {
    public static final GetStatistics instance = new GetStatistics();

    private GetStatistics() {
    }
  }

  public static class PublishAxisUpdate implements AxisRequest {
    public static final PublishAxisUpdate instance = new PublishAxisUpdate();

    private PublishAxisUpdate() {
    }
  }

  public interface AxisResponse {
  }

  public static class AxisStarted implements AxisResponse {
    static final AxisStarted instance = new AxisStarted();

    private AxisStarted() {
    }
  }

  @SuppressWarnings("unused")
  public static class AxisFinished implements AxisResponse {
    static final AxisFinished instance = new AxisFinished();

    private AxisFinished() {
    }
  }

  @SuppressWarnings("WeakerAccess")
  public static class AxisUpdate implements AxisResponse {
    public final String axisName;
    public final AxisState state;
    public final int current;
    public final boolean inLowLimit;
    public final boolean inHighLimit;
    public final boolean inHomed;

    public AxisUpdate(String axisName, AxisState state, int current, boolean inLowLimit, boolean inHighLimit, boolean inHomed) {
      this.axisName = axisName;
      this.state = state;
      this.current = current;
      this.inLowLimit = inLowLimit;
      this.inHighLimit = inHighLimit;
      this.inHomed = inHomed;
    }
  }

  @SuppressWarnings("unused")
  public static class AxisFailure implements AxisResponse {
    public final String reason;

    public AxisFailure(String reason) {
      this.reason = reason;
    }
  }

  public static class AxisStatistics implements AxisResponse {
    public final String axisName;
    public final int initCount;
    public final int moveCount;
    public final int homeCount;
    public final int limitCount;
    public final int successCount;
    public final int failureCount;
    public final int cancelCount;

    public AxisStatistics(String axisName, int initCount, int moveCount, int homeCount, int limitCount,
                          int successCount, int failureCount, int cancelCount) {
      this.axisName = axisName;
      this.initCount = initCount;
      this.moveCount = moveCount;
      this.homeCount = homeCount;
      this.limitCount = limitCount;
      this.successCount = successCount;
      this.failureCount = failureCount;
      this.cancelCount = cancelCount;
    }

    @Override
    public String toString() {
      return "name: " + axisName
        + ", inits: " + initCount
        + ", moves: " + moveCount
        + ", homes: " + homeCount
        + ", limits: " + limitCount
        + ", success: " + successCount
        + ", fails: " + failureCount
        + ", cancels: " + cancelCount;
    }
  }

  // Internal
  public interface InternalMessages {
  }

  public static class DatumComplete implements InternalMessages {
    public static final DatumComplete instance = new DatumComplete();

    private DatumComplete() {
    }
  }

  public static class HomeComplete implements InternalMessages {
    public final int position;

    public HomeComplete(int position) {
      this.position = position;
    }
  }

  public static class MoveComplete implements InternalMessages {
    public final int position;

    public MoveComplete(int position) {
      this.position = position;
    }
  }

  public static class InitialState implements InternalMessages {
    public static final InitialState instance = new InitialState();

    private InitialState() {
    }
  }

  @SuppressWarnings("unused")
  public static class InitialStatistics implements InternalMessages {
    public static final InitialStatistics instance = new InitialStatistics();

    private InitialStatistics() {
    }
  }

  // Helper functions in object for testing
  // limitMove clamps the request value to the hard limits
  public static int limitMove(AxisConfig ac, int request) {
    return Math.max(Math.min(request, ac.highLimit), ac.lowLimit);
  }

  // Check to see if position is in the "limit" zones
  public static boolean isHighLimit(AxisConfig ac, int current) {
    return current >= ac.highUser;
  }

  public static boolean isLowLimit(AxisConfig ac, int current) {
    return current <= ac.lowUser;
  }

  public static boolean isHomed(AxisConfig ac, int current) {
    return current == ac.home;
  }
}

// --- MotionWorker actor ---

@SuppressWarnings("WeakerAccess")
class MotionWorker extends AbstractTimeServiceScheduler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  int start;
  int destinationIn;
  int delayInMS;
  ActorRef replyTo;
  boolean diagFlag;

  int destination;
  int numSteps;
  int stepSize;
  int stepCount = 0;
  // Can be + or -
  boolean cancelFlag = false;
  long delayInNanoSeconds;
  int current;

  private MotionWorker(int start, int destinationIn, int delayInMS, ActorRef replyTo, boolean diagFlag) {
    this.start = start;
    current = start;
    this.destinationIn = destinationIn;
    destination = destinationIn;
    this.delayInMS = delayInMS;
    this.replyTo = replyTo;
    this.diagFlag = diagFlag;

    numSteps = calcNumSteps(start, destination);
    stepSize = calcStepSize(start, destination, numSteps);
    delayInNanoSeconds = delayInMS * 1000000;
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receive() {
    return ReceiveBuilder
      .match(Start.class, e -> {
        if (diagFlag) diag("Starting", start, numSteps);
        replyTo.tell(e, self());
        scheduleOnce(TimeService.localTimeNow().plusNanos(delayInNanoSeconds), self(), new Tick(start + stepSize));
      })
      .match(Tick.class, e -> {
        current = e.current;
        replyTo.tell(e, self());

        // Keep a count of steps in this MotionWorker instance
        stepCount += 1;

        // If we are on the last step of a move, then distance equals 0
        int distance = calcDistance(current, destination);
        boolean done = distance == 0;
        // To fix rounding errors, if last step set current to destination
        boolean last = lastStep(current, destination, stepSize);
        int nextPos = last ? destination : current + stepSize;
        if (diagFlag)
          log.info("currentIn: " + current + ", distance: " + distance + ", stepSize: " + stepSize + ", done: " + done + ", nextPos: " + nextPos);
        if (!done && !cancelFlag)
          scheduleOnce(TimeService.localTimeNow().plusNanos(delayInNanoSeconds), self(), new Tick(nextPos));
        else self().tell(new End(current), self());
      })
      .match(MoveUpdate.class, e -> {
        destination = e.destination;
        numSteps = calcNumSteps(current, destination);
        stepSize = calcStepSize(current, destination, numSteps);
        log.info("NEW dest: " + destination + ", numSteps: " + numSteps + ", stepSize: " + stepSize);
      })
      .match(Cancel.class, e -> {
        if (diagFlag) log.debug("Worker received cancel");
        cancelFlag = true; // Will cause to leave on next Tick
      })
      .match(End.class, e -> {
        replyTo.tell(e, self());
        if (diagFlag) diag("End", e.finalpos, numSteps);
        // When the actor has nothing else to do, it should stop
        context().stop(self());
      })
      .matchAny(x -> log.warning("Unexpected message in MotionWorker: " + x))
      .build();
  }

  void diag(String hint, int current, int stepValue) {
    log.info(hint + ": start=" + start + ", dest=" + destination + ", totalSteps: " + stepValue + ", current=" + current);
  }


  // -- static defs --

  /**
   * Used to create the actor
   */
  public static Props props(final int start, final int destinationIn, final int delayInMS, final ActorRef replyTo, final boolean diagFlag) {
    return Props.create(new Creator<MotionWorker>() {
      private static final long serialVersionUID = 1L;

      @Override
      public MotionWorker create() throws Exception {
        return new MotionWorker(start, destinationIn, delayInMS, replyTo, diagFlag);
      }
    });
  }

  public interface MotionWorkerMsgs {
  }

  public static class Start implements MotionWorkerMsgs {
    public final static Start instance = new Start();

    private Start() {
    }
  }

  public static class End implements MotionWorkerMsgs {
    int finalpos;

    public End(int finalpos) {
      this.finalpos = finalpos;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      End end = (End) o;

      return finalpos == end.finalpos;
    }

    @Override
    public int hashCode() {
      return finalpos;
    }
  }

  public static class Tick implements MotionWorkerMsgs {
    int current;

    public Tick(int current) {
      this.current = current;
    }
  }

  public static class MoveUpdate implements MotionWorkerMsgs {
    int destination;

    public MoveUpdate(int destination) {
      this.destination = destination;
    }
  }

  public static class Cancel implements MotionWorkerMsgs {
    public final static Cancel instance = new Cancel();

    private Cancel() {
    }
  }

  public static int calcNumSteps(int start, int end) {
    int diff = Math.abs(start - end);
    if (diff <= 5) return 1;
    if (diff <= 20) return 2;
    if (diff <= 500) return 5;
    return 10;
  }

  public static int calcStepSize(int current, int destination, int steps) {
    return (destination - current) / steps;
  }

  public static int calcDistance(int current, int destination) {
    return Math.abs(current - destination);
  }

  public static boolean lastStep(int current, int destination, int stepSize) {
    return calcDistance(current, destination) <= Math.abs(stepSize);
  }
}


