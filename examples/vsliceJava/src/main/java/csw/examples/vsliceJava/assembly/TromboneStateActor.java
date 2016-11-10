package csw.examples.vsliceJava.assembly;


import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import csw.util.config.*;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import static javacsw.util.config.JItems.jset;
import static javacsw.util.config.JItems.jvalue;

/**
 * Note that this state actor is not a listener for events. Only the client listens.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class TromboneStateActor extends AbstractActor {
  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private TromboneStateActor() {
    receive(stateReceive(new TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault)));
  }


  /**
   * This stateReceive must be added to the actor's receive chain.
   * This is called when some other actor changes the state to ensure the value is updated for all users
   *
   * @return Akka Receive partial function
   */
  private PartialFunction<Object, BoxedUnit> stateReceive(TromboneState currentState) {
    return ReceiveBuilder.
      match(SetState.class, t -> {
        TromboneState ts = t.tromboneState;
        if (!ts.equals(currentState)) {
          context().system().eventStream().publish(ts);
          context().become(stateReceive(ts));
        }
      }).
      match(GetState.class, t -> sender().tell(currentState, self())).
      matchAny(t -> log.warning("TromboneStateActor received an unexpected message: " + t)).
      build();
  }

  // --- static data ---

  public static Props props() {
    return Props.create(new Creator<TromboneStateActor>() {
      private static final long serialVersionUID = 1L;

      @Override
      public TromboneStateActor create() throws Exception {
        return new TromboneStateActor();
      }
    });
  }

  // Keys for state telemetry item
  public static final Choice cmdUninitialized = new Choice("uninitialized");
  public static final Choice cmdReady = new Choice("ready");
  public static final Choice cmdBusy = new Choice("busy");
  public static final Choice cmdContinuous = new Choice("continuous");
  public static final Choice cmdError = new Choice("error");
  public static final ChoiceKey cmdKey = new ChoiceKey("cmd", Choices.fromChoices(cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError));
  public static final ChoiceItem cmdDefault = cmdItem(cmdUninitialized);

  public static Choice cmd(TromboneState ts) {
    return jvalue(ts.cmd);
  }

  /**
   * A convenience method to set the cmdItem choice
   *
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
  public static final ChoiceItem moveDefault = moveItem(moveUnindexed);

  public static Choice move(TromboneState ts) {
    return jvalue(ts.move);
  }

  /**
   * A convenience method to set the moveItem choice
   *
   * @param ch one of the move choices
   * @return a ChoiceItem with the choice value
   */
  public static ChoiceItem moveItem(Choice ch) {
    return jset(moveKey, ch);
  }

  public static final BooleanKey sodiumKey = new BooleanKey("sodiumLayer");
  public static final BooleanItem sodiumLayerDefault = sodiumItem(false);

  public static boolean sodiumLayer(TromboneState ts) {
    return jvalue(ts.sodiumLayer);
  }

  /**
   * A convenience method to set the sodium layer boolean value indicating the sodium layer has been set
   *
   * @param flag trur or false
   * @return a BooleanItem with the Boolean value
   */
  public static BooleanItem sodiumItem(Boolean flag) {
    return jset(sodiumKey, flag);
  }

  public static final BooleanKey nssKey = new BooleanKey("nss");
  public static final BooleanItem nssDefault = nssItem(false);

  public static Boolean nss(TromboneState ts) {
    return jvalue(ts.nss);
  }

  /**
   * A convenience method to set the NSS enabled boolean value
   *
   * @param flag true or false
   * @return a BooleanItem with the Boolean value
   */
  public static BooleanItem nssItem(Boolean flag) {
    return jset(nssKey, flag);
  }

  public static final TromboneState defaultTromboneState = new TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault);

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

  /**
   * Update the current state with a TromboneState
   */
  public static class SetState {
    public final TromboneState tromboneState;

    /**
     * Constructor
     *
     * @param tromboneState the new trombone state value
     */
    public SetState(TromboneState tromboneState) {
      this.tromboneState = tromboneState;
    }

    /**
     * Alternate way to create the SetState message using items
     *
     * @param cmd         a ChoiceItem created with cmdItem
     * @param move        a ChoiceItem created with moveItem
     * @param sodiumLayer a BooleanItem created with sodiumItem
     * @param nss         a BooleanItem created with nssItem
     */
    public SetState(ChoiceItem cmd, ChoiceItem move, BooleanItem sodiumLayer, BooleanItem nss) {
      this(new TromboneState(cmd, move, sodiumLayer, nss));
    }

    /**
     * Alternate way to create the SetState message using primitives
     *
     * @param cmd         a Choice for the cmd value (i.e. cmdReady, cmdBusy, etc.)
     * @param move        a Choice for the mvoe value (i.e. moveUnindexed, moveIndexing, etc.)
     * @param sodiumLayer a boolean for sodium layer value
     * @param nss         a boolan for the NSS in use value
     */
    public SetState(Choice cmd, Choice move, Boolean sodiumLayer, Boolean nss) {
      this(new TromboneState(cmdItem(cmd), moveItem(move), sodiumItem(sodiumLayer), nssItem(nss)));
    }
  }

  /**
   * A message that causes the current state to be sent back to the sender
   */
  public static class GetState {
  }
}

