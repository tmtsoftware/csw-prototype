package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, Props}
import csw.util.config._

/**
 * Note that this state actor is not a listener for events. Only the client listens.
 */
class TromboneStateActor extends Actor with ActorLogging {

  import TromboneStateActor._

  // This actor subscribes to TromboneState using the EventBus
  //context.system.eventStream.subscribe(self, classOf[TromboneState])

  def receive = stateReceive(TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault))

  /**
   * This stateReceive must be added to the actor's receive chain.
   * This is called when some other actor changes the state to ensure the value is updated for all users
   *
   * @return Akka Receive partial function
   */
  def stateReceive(currentState: TromboneState): Receive = {

    case SetState(ts) =>
      if (ts != currentState) {
        context.system.eventStream.publish(ts)
        context.become(stateReceive(ts))
      }

    case GetState =>
      sender() ! currentState

    case x => log.error(s"TromboneStateActor received an unexpected message: $x")
  }

}

trait TromboneStateClient {
  this: Actor =>

  import TromboneStateActor._

  // This actor subscribes to TromboneState using the EventBus
  context.system.eventStream.subscribe(self, classOf[TromboneState])

  private var internalState = defaultTromboneState

  def stateReceive: Receive = {
    case ts: TromboneState =>
      internalState = ts
  }

  /**
   * The currentState as a TromonbeState is returned.
   * @return TromboneState current state
   */
  def currentState: TromboneState = internalState
}

object TromboneStateActor {

  def props(): Props = Props(new TromboneStateActor())

  // Keys for state telemetry item
  val cmdUninitialized = Choice("uninitialized")
  val cmdReady = Choice("ready")
  val cmdBusy = Choice("busy")
  val cmdContinuous = Choice("continuous")
  val cmdError = Choice("error")
  val cmdKey = ChoiceKey("cmd", cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError)
  val cmdDefault = cmdItem(cmdUninitialized)
  def cmd(ts: TromboneState): Choice = ts.cmd.head

  /**
   * A convenience method to set the cmdItem choice
   * @param ch one of the cmd choices
   * @return a ChoiceItem with the choice value
   */
  def cmdItem(ch: Choice): ChoiceItem = cmdKey.set(ch)

  val moveUnindexed = Choice("unindexed")
  val moveIndexing = Choice("indexing")
  val moveIndexed = Choice("indexed")
  val moveMoving = Choice("moving")
  val moveKey = ChoiceKey("move", moveUnindexed, moveIndexing, moveIndexed, moveMoving)
  val moveDefault = moveItem(moveUnindexed)
  def move(ts: TromboneState): Choice = ts.move.head

  /**
   * A convenience method to set the moveItem choice
   * @param ch one of the move choices
   * @return a ChoiceItem with the choice value
   */
  def moveItem(ch: Choice): ChoiceItem = moveKey.set(ch)

  def sodiumKey = BooleanKey("sodiumLayer")
  val sodiumLayerDefault = sodiumItem(false)
  def sodiumLayer(ts: TromboneState): Boolean = ts.sodiumLayer.head

  /**
   * A convenience method to set the sodium layer boolean value indicating the sodium layer has been set
   * @param flag trur or false
   * @return a BooleanItem with the Boolean value
   */
  def sodiumItem(flag: Boolean): BooleanItem = sodiumKey.set(flag)

  def nssKey = BooleanKey("nss")
  val nssDefault = nssItem(false)
  def nss(ts: TromboneState): Boolean = ts.nss.head

  /**
   * A convenience method to set the NSS enabled boolean value
   * @param flag true or false
   * @return a BooleanItem with the Boolean value
   */
  def nssItem(flag: Boolean): BooleanItem = nssKey.set(flag)

  val defaultTromboneState = TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault)

  /**
   * This class is sent to the publisher for publishing when any state value changes
   *
   * @param cmd         the current cmd state
   * @param move        the current move state
   * @param sodiumLayer the current sodiumLayer flag, set when elevation has been set
   * @param nss         the current NSS mode flag
   */
  case class TromboneState(cmd: ChoiceItem, move: ChoiceItem, sodiumLayer: BooleanItem, nss: BooleanItem)

  /**
   * Update the current state with a TromboneState
   * @param tromboneState the new trombone state value
   */
  case class SetState(tromboneState: TromboneState)

  object SetState {
    /**
     * Alternate way to create the SetState message using items
     * @param cmd a ChoiceItem created with cmdItem
     * @param move a ChoiceItem created with moveItem
     * @param sodiumLayer a BooleanItem created with sodiumItem
     * @param nss a BooleanItem created with nssItem
     * @return a new SetState message instance
     */
    def apply(cmd: ChoiceItem, move: ChoiceItem, sodiumLayer: BooleanItem, nss: BooleanItem): SetState = SetState(TromboneState(cmd, move, sodiumLayer, nss))

    /**
     * Alternate way to create the SetState message using primitives
     * @param cmd a Choice for the cmd value (i.e. cmdReady, cmdBusy, etc.)
     * @param move a Choice for the mvoe value (i.e. moveUnindexed, moveIndexing, etc.)
     * @param sodiumLayer a boolean for sodium layer value
     * @param nss a boolan for the NSS in use value
     * @return a new SetState message instance
     */
    def apply(cmd: Choice, move: Choice, sodiumLayer: Boolean, nss: Boolean): SetState = SetState(TromboneState(cmdItem(cmd), moveItem(move), sodiumItem(sodiumLayer), nssItem(nss)))
  }

  /**
   * A message that causes the current state to be sent back to the sender
   */
  case object GetState

}
