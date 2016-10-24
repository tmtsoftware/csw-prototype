package csw.examples.vslice.assembly

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging}
import csw.examples.vslice.assembly.TromboneStateActor.TromboneState
import csw.util.config._

/**
 * TMT Source Code: 10/22/16.
 */
class TromboneStateActor extends Actor with ActorLogging {

  import TromboneStateActor._

  // This actor subscribes to TromboneState using the EventBus
  //context.system.eventStream.subscribe(self, classOf[TromboneState])

  //  def unsubscribeState() = context.system.eventStream.unsubscribe(self, classOf[TromboneState])

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
        log.info(s">>>>>>>>>>>>>>>>>>>>>>          Got State Update: $ts")
        context.system.eventStream.publish(ts)
        context.become(stateReceive(ts))
      }

    case getState => sender() ! currentState
  }

}

trait TromboneStateClient {
  self => Actor

  import TromboneStateActor._

  private var currentState = defaultTromboneState

  def stateReceive: Receive = {
    case ts: TromboneState => currentState = ts
  }

  /**
   * Public method to access the current value of the cmd state choice
   * @return the cmd choice value
   */
  def cmd: Choice = currentState.cmd.head

  /**
   * Public method to access the current value of the move state choice
   * @return the move choice value
   */
  def move: Choice = currentState.move.head

  /**
   * Public method to access the current value of the sodiumLayer state boolean
   * @return a Boolean indicating if the sodium layer has been set
   */
  def sodiumLayer: Boolean = currentState.sodiumLayer.head

  /**
   * Public method to access the current value of the nss state Boolean
   * @return a Boolean value inicating if the NSS is enabled
   */
  def nss: Boolean = currentState.nss.head

}

object TromboneStateActor {

  // Keys for state telemetry item
  val cmdUninitialized = Choice("uninitialized")
  val cmdReady = Choice("ready")
  val cmdBusy = Choice("busy")
  val cmdContinuous = Choice("continuous")
  val cmdError = Choice("error")
  val cmdKey = ChoiceKey("cmd", cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError)
  val cmdDefault = cmdItem(cmdUninitialized)

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

  /**
   * A convenience method to set the moveItem choice
   * @param ch one of the move choices
   * @return a ChoiceItem with the choice value
   */
  def moveItem(ch: Choice): ChoiceItem = moveKey.set(ch)

  def sodiumKey = BooleanKey("sodiumLayer")
  val sodiumLayerDefault = sodiumItem(false)

  /**
   * A convenience method to set the sodium layer boolean value indicating the sodium layer has been set
   * @param flag trur or false
   * @return a BooleanItem with the Boolean value
   */
  def sodiumItem(flag: Boolean): BooleanItem = sodiumKey.set(flag)

  def nssKey = BooleanKey("nss")
  val nssDefault = nssItem(false)

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

  case class SetState(ts: TromboneState)

  case object GetState

}
