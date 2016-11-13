package csw.examples.vsliceJava.assembly;

import akka.actor.Actor;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

@SuppressWarnings({"unused", "WeakerAccess"})
public interface TromboneStateClient extends Actor {

  /**
   * Sets the current trombone state.
   * Note: Since Java interfaces can't have non-static local variables, this needs to be defined in the implementing class.
   *
   * Note: Implementing Java based actor classes must subscribe to TromboneState using the EventBus:
   *  context().system().eventStream().subscribe(self(), TromboneState.class);
   */
  void setCurrentState(TromboneStateActor.TromboneState ts);

  default PartialFunction<Object, BoxedUnit> stateReceive() {
    return ReceiveBuilder.
      match(TromboneStateActor.TromboneState.class, ts -> {
        System.out.println("Got state: " + ts);
        setCurrentState(ts);
      }).
      build();
  }
}
