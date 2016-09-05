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
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "CodeBlock2Expr"})
public class SingleAxisSimulator extends AbstractTimeServiceScheduler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);


  private final AxisConfig axisConfig;
  private final Optional<ActorRef> replyTo;

  // The following are state information for the axis. These values are updated while the axis runs
  // This is safe because there is no way to change the variables other than within this actor
  // When created, the current is set to the start current
  private int current;
  private boolean inLowLimit = false;
  private boolean inHighLimit = false;
  private boolean inHome = false;
  private AxisState axisState = AXIS_IDLE;

  // Statistics for status
  private int initCount = 0;
  // Number of init requests
  private int moveCount = 0;
  // Number of move requests
  private int homeCount = 0;
  // Number of home requests
  private int limitCount = 0;
  // Number of times in a limit
  private int successCount = 0;
  // Number of successful requests
  private int failureCount = 0;
  // Number of failed requests
  private int cancelCount = 0; // Number of times a move has been cancelled


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
    if (axisConfig.home > axisConfig.lowUser)
      throw new AssertionError("home position must be greater than lowUser value: " + axisConfig.lowUser);
    if (axisConfig.home < axisConfig.highUser)
      throw new AssertionError("home position must be less than highUser value: " + axisConfig.highUser);

    getContext().become(idleReceive());

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
        calcLimitsAndStats();
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
      .matchEquals(Home.instance, e -> {
        axisState = AXIS_MOVING;
        log.debug("AxisHome: " + axisState);
        update(replyTo, AxisStarted.instance);
        Props props = MotionWorker.props(current, axisConfig.home, 2, self(), false);
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
        calcLimitsAndStats();
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
        calcLimitsAndStats();
        // Stats
        successCount++;
        // Send Update
        update(replyTo, getState());
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


  void calcLimitsAndStats() {
    inHighLimit = isHighLimit(axisConfig, current);
    inLowLimit = isLowLimit(axisConfig, current);
    if (inHighLimit || inLowLimit) limitCount += 1;
    inHome = isHomed(axisConfig, current);
    if (inHome) homeCount += 1;
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

  public static class AxisConfig {
    String axisName;
    int lowLimit;
    int lowUser;
    int highUser;
    int highLimit;
    int home;
    int startPosition;
    int stepDelayMS;

    public AxisConfig(String axisName, int lowLimit, int lowUser, int highUser, int highLimit, int home, int startPosition, int stepDelayMS) {
      this.axisName = axisName;
      this.lowLimit = lowLimit;
      this.lowUser = lowUser;
      this.highUser = highUser;
      this.highLimit = highLimit;
      this.home = home;
      this.startPosition = startPosition;
      this.stepDelayMS = stepDelayMS;
    }
  }

  public enum AxisState {
    AXIS_IDLE,
    AXIS_MOVING,
    AXIS_ERROR,
  }

  interface AxisRequest {
  }

  static class Home implements AxisRequest {
    public static final Home instance = new Home();

    private Home() {
    }
  }

  static class Datum implements AxisRequest {
    public static final Datum instance = new Datum();

    private Datum() {
    }
  }

  static class Move implements AxisRequest {
    int position;
    boolean diagFlag;

    public Move(int position, boolean diagFlag) {
      this.position = position;
      this.diagFlag = diagFlag;
    }
  }

  @SuppressWarnings("unused")
  static class CancelMove implements AxisRequest {
    public static final CancelMove instance = new CancelMove();

    private CancelMove() {
    }
  }

  @SuppressWarnings("unused")
  static class GetStatistics implements AxisRequest {
    public static final GetStatistics instance = new GetStatistics();

    private GetStatistics() {
    }
  }


  interface AxisResponse {
  }

  static class AxisStarted implements AxisResponse {
    static final AxisStarted instance = new AxisStarted();

    private AxisStarted() {
    }
  }

  @SuppressWarnings("unused")
  static class AxisFinished implements AxisResponse {
    static final AxisFinished instance = new AxisFinished();

    private AxisFinished() {
    }
  }

  static class AxisUpdate implements AxisResponse {
    String axisName;
    AxisState state;
    int current;
    boolean inLowLimit;
    boolean inHighLimit;
    boolean inHomed;

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
  static class AxisFailure implements AxisResponse {
    String reason;

    public AxisFailure(String reason) {
      this.reason = reason;
    }
  }

  static class AxisStatistics implements AxisResponse {
    String axisName;
    int initCount;
    int moveCount;
    int homeCount;
    int limitCount;
    int successCount;
    int failureCount;
    int cancelCount;

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
  interface InternalMessages {
  }

  static class DatumComplete implements InternalMessages {
    static final DatumComplete instance = new DatumComplete();

    private DatumComplete() {
    }
  }

  static class HomeComplete implements InternalMessages {
    int position;

    public HomeComplete(int position) {
      this.position = position;
    }
  }

  static class MoveComplete implements InternalMessages {
    int position;

    public MoveComplete(int position) {
      this.position = position;
    }
  }

  static class InitialState implements InternalMessages {
    static final InitialState instance = new InitialState();

    private InitialState() {
    }
  }

  @SuppressWarnings("unused")
  static class InitialStatistics implements InternalMessages {
    static final InitialStatistics instance = new InitialStatistics();

    private InitialStatistics() {
    }
  }

  // Helper functions in object for testing
  // limitMove clamps the request value to the hard limits
  static int limitMove(AxisConfig ac, int request) {
    return Math.max(Math.min(request, ac.highLimit), ac.lowLimit);
  }

  // Check to see if position is in the "limit" zones
  static boolean isHighLimit(AxisConfig ac, int current) {
    return current >= ac.highUser;
  }

  static boolean isLowLimit(AxisConfig ac, int current) {
    return current <= ac.lowUser;
  }

  static boolean isHomed(AxisConfig ac, int current) {
    return current == ac.home;
  }

  // Short-cut to forward a messaage to the optional replyTo actor
  void update(Optional<ActorRef> replyTo, Object msg) {
    if (replyTo.isPresent()) {
      replyTo.get().tell(msg, self());
    }
  }
}

// --- MotionWorker actor ---

class MotionWorker extends AbstractTimeServiceScheduler {
  LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  int start;
  int destinationIn;
  int delayInMS;
  ActorRef replyTo;
  boolean diagFlag;

  int destination;
  int numSteps = calcNumSteps(start, destination);
  int stepSize = calcStepSize(start, destination, numSteps);
  int stepCount = 0;
  // Can be + or -
  boolean cancelFlag = false;
  long delayInNanoSeconds = delayInMS * 1000000;
  int current;

  private MotionWorker(int start, int destinationIn, int delayInMS, ActorRef replyTo, boolean diagFlag) {
    this.start = start;
    this.destinationIn = destinationIn;
    this.delayInMS = delayInMS;
    this.replyTo = replyTo;
    this.diagFlag = diagFlag;

    destination = destinationIn;
    current = start;
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

  interface MotionWorkerMsgs {
  }

  static class Start implements MotionWorkerMsgs {
    public final static Start instance = new Start();

    private Start() {
    }
  }

  static class End implements MotionWorkerMsgs {
    int finalpos;

    public End(int finalpos) {
      this.finalpos = finalpos;
    }
  }

  static class Tick implements MotionWorkerMsgs {
    int current;

    public Tick(int current) {
      this.current = current;
    }
  }

  static class MoveUpdate implements MotionWorkerMsgs {
    int destination;

    public MoveUpdate(int destination) {
      this.destination = destination;
    }
  }

  static class Cancel implements MotionWorkerMsgs {
    public final static Cancel instance = new Cancel();

    private Cancel() {
    }
  }

  int calcNumSteps(int start, int end) {
    int diff = Math.abs(start - end);
    if (diff <= 5) return 1;
    if (diff <= 20) return 2;
    if (diff <= 500) return 5;
    return 10;
  }

  int calcStepSize(int current, int destination, int steps) {
    return (destination - current) / steps;
  }

  int calcDistance(int current, int destination) {
    return Math.abs(current - destination);
  }

  boolean lastStep(int current, int destination, int stepSize) {
    return calcDistance(current, destination) <= Math.abs(stepSize);
  }
}


