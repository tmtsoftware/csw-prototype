package csw.examples.vsliceJava.assembly;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import csw.util.config.*;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Optional;

import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JItems.jvalue;

@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused", "WeakerAccess"})
public abstract class TromboneStateHandler extends AbstractActor {

  protected final ChoiceItem cmdDefault = jset(cmdKey, cmdUninitialized);
  protected final ChoiceItem moveDefault = jset(moveKey, moveUnindexed);
  protected final BooleanItem sodiumLayerDefault = jset(sodiumKey, false);
  protected final BooleanItem nssDefault = jset(nssKey, false);

  protected TromboneState _tromboneState = new TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault);

  // Constructor
  protected TromboneStateHandler() {
    // This actor subscribes to TromboneState using the EventBus
    context().system().eventStream().subscribe(self(), TromboneState.class);
  }

  protected void unsubscribeState() {
    context().system().eventStream().unsubscribe(self(), TromboneState.class);
  }

  /**
   * This stateReceive must be added to the actor's receive chain.
   * This is called when some other actor changes the state to ensure the value is updated for all users
   *
   * @return Akka Receive partial function
   */
  protected PartialFunction<Object, BoxedUnit> stateReceive(
    BooleanItem nssInUse, ActorRef followActor, ActorRef eventSubscriber, Optional<ActorRef> tromboneHCD) {
    return ReceiveBuilder.
      match(TromboneState.class, ts -> _tromboneState = ts).
      build();
  }

  /**
   * Method for subclasses to access the current value of the cmd state choice
   * @return the cmd choice value
   */
  protected Choice cmd() {
    return getCmd();
  }

  /**
   * Method for subclasses to access the current value of the move state choice
   * @return the move choice value
   */
  protected Choice move() {
    return getMove();
  }

  /**
   * Method for subclasses to access the current value of the sodiumLayer state boolean
   * @return a Boolean indicating if the sodium layer has been set
   */
  protected Boolean sodiumLayer() {
    return getSodiumLayer();
  }

  /**
   * Method for subclasses to access the current value of the nss state Boolean
   * @return a Boolean value inicating if the NSS is enabled
   */
  protected Boolean nss() {
    return getNss();
  }

  // This bit is so that I can have a get and set that is the same allowing me to test for changes
  private Choice getCmd() {
    return jvalue(_tromboneState.cmd);
  }

  private Choice getMove() {
    return jvalue(_tromboneState.move);
  }

  private Boolean getSodiumLayer() {
    return jvalue(_tromboneState.sodiumLayer);
  }

  private Boolean getNss() {
    return jvalue(_tromboneState.nss);
  }

  protected TromboneState tromboneState() {
    return _tromboneState;
  }

  protected void state(Choice cmd, Choice move, boolean sodiumLayer, boolean nss) {
    boolean update = getCmd() != cmd || getMove() != move || getSodiumLayer() != sodiumLayer || getNss() != nss;
    if (update) {
      _tromboneState = new TromboneState(cmdItem(cmd), moveItem(move), jset(sodiumKey, sodiumLayer), jset(nssKey, nss));
      context().system().eventStream().publish(_tromboneState);
    }
  }

  // --- static defs ---

  /**
   * This class is sent to the publisher for publishing when any state value changes
   */
  public static class TromboneState {
    public final ChoiceItem cmd;
    public final ChoiceItem move;
    public final BooleanItem sodiumLayer;
    public final BooleanItem nss;

    /**
     * Constructor
     *
     * @param cmd         the current cmd state
     * @param move        the current move state
     * @param sodiumLayer the current sodiumLayer flag, set when elevation has been set
     * @param nss         the current NSS mode flag
     */
    public TromboneState(ChoiceItem cmd, ChoiceItem move, BooleanItem sodiumLayer, BooleanItem nss) {
      this.cmd = cmd;
      this.move = move;
      this.sodiumLayer = sodiumLayer;
      this.nss = nss;
    }
  }

  // Keys for state telemetry item
  public static final Choice cmdUninitialized = new Choice("uninitialized");
  public static final Choice cmdReady = new Choice("ready");
  public static final Choice cmdBusy = new Choice("busy");
  public static final Choice cmdContinuous = new Choice("continuous");
  public static final Choice cmdError = new Choice("error");
  public static final ChoiceKey cmdKey = new ChoiceKey("cmd", Choices.fromChoices(cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError));

  /**
   * A convenience method to set the cmdItem choice
   * @param ch one of the cmd choices
   * @return a ChoiceItem with the choice value
   */
  public static ChoiceItem cmdItem(Choice ch) {
    return jset(cmdKey, ch);
  }

  public static final Choice moveUnindexed = new Choice("unindexed");
  public static final Choice moveIndexing = new Choice("indexing");
  public static final Choice moveIndexed = new Choice("indexed");
  public static final Choice moveMoving = new Choice("moving");
  public static final ChoiceKey moveKey = new ChoiceKey("move", Choices.fromChoices(moveUnindexed, moveIndexing, moveIndexed, moveMoving));

  /**
   * A convenience method to set the moveItem choice
   * @param ch one of the move choices
   * @return a ChoiceItem with the choice value
   */
  public static ChoiceItem moveItem(Choice ch) {
    return jset(moveKey, ch);
  }

  public static final BooleanKey sodiumKey = new BooleanKey("sodiumLayer");

  /**
   * A convenience method to set the sodium layer boolean value indicating the sodium layer has been set
   * @param flag trur or false
   * @return a BooleanItem with the Boolean value
   */
  public static BooleanItem sodiumItem(Boolean flag) {
    return jset(sodiumKey, flag);
  }

  public static final BooleanKey nssKey = new BooleanKey("nss");

  /**
   * A convenience method to set the NSS enabled boolean value
   * @param flag true or false
   * @return a BooleanItem with the Boolean value
   */
  public static BooleanItem nssItem(Boolean flag) {
    return jset(nssKey, flag);
  }
}
