package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorRef}
import csw.util.config._


trait TromboneStateHandler {
  this: Actor =>

  import TromboneStateHandler._

  private val cmdDefault = cmdKey -> cmdUninitialized
  private val moveDefault = moveKey -> moveUnindexed
  private val sodiumLayerDefault = sodiumKey -> false
  private val nssDefault = nssKey -> false

  private var tromboneState = TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault)

  // This actor subscribes to TromboneState using the EventBus
  context.system.eventStream.subscribe(self, classOf[TromboneState])

  /**
    * This stateReceive must be added to the actor's receive chain.
    * This is called when some other actor changes the state to ensure the value is updated for all users
    *
    * @return Akka Receive partial function
    */
  def stateReceive: Receive = {
    case ts: TromboneState => tromboneState = ts
  }

  /**
    * Public method to access the current value of the cmd state choice
    * @return the cmd choice value
    */
  def cmd: Choice = getCmd

  /**
    * Public method to access the current value of the move state choice
    * @return the move choice value
    */
  def move: Choice = getMove

  /**
    * Public method to access the current value of the sodiumLayer state boolean
    * @return a Boolean indicating if the sodium layer has been set
    */
  def sodiumLayer: Boolean = getSodiumLayer

  /**
    * Public method to access the current value of the nss state Boolean
    * @return a Boolean value inicating if the NSS is enabled
    */
  def nss: Boolean = getNss


  // This bit is so that I can have a get and set that is the same allowing me to test for changes
  private def getCmd = tromboneState.cmd.head

  private def getMove: Choice = tromboneState.move.head

  private def getSodiumLayer: Boolean = tromboneState.sodiumLayer.head

  private def getNss: Boolean = tromboneState.nss.head

  def state(cmd: Choice = cmd, move: Choice = move, sodiumLayer: Boolean = sodiumLayer, nss: Boolean = nss): Unit = {
    val update = getCmd != cmd || getMove != move || getSodiumLayer != sodiumLayer || getNss != nss
    if (update) {
      tromboneState = TromboneState(cmdItem(cmd), moveItem(move), sodiumKey.set(sodiumLayer), nssKey.set(nss))
      context.system.eventStream.publish(tromboneState)
    }
  }
}

object TromboneStateHandler {

  /**
    * This class is sent to the publisher for publishing when any state value changes
    *
    * @param cmd         the current cmd state
    * @param move        the current move state
    * @param sodiumLayer the current sodiumLayer flag, set when elevation has been set
    * @param nss         the current NSS mode flag
    */
  case class TromboneState(cmd: ChoiceItem, move: ChoiceItem, sodiumLayer: BooleanItem, nss: BooleanItem)

  // Keys for state telemetry item
  val cmdUninitialized = Choice("uninitialized")
  val cmdReady = Choice("ready")
  val cmdBusy = Choice("busy")
  val cmdContinuous = Choice("continuous")
  val cmdError = Choice("error")
  val cmdKey = ChoiceKey("cmd", cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError)

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

  /**
    * A convenience method to set the moveItem choice
    * @param ch one of the move choices
    * @return a ChoiceItem with the choice value
    */
  def moveItem(ch: Choice): ChoiceItem = moveKey.set(ch)

  def sodiumKey = BooleanKey("sodiumLayer")

  /**
    * A convenience method to set the sodium layer boolean value indicating the sodium layer has been set
    * @param flag trur or false
    * @return a BooleanItem with the Boolean value
    */
  def sodiumItem(flag: Boolean): BooleanItem = sodiumKey.set(flag)

  def nssKey = BooleanKey("nss")

  /**
    * A convenience method to set the NSS enabled boolean value
    * @param flag true or false
    * @return a BooleanItem with the Boolean value
    */
  def nssItem(flag: Boolean): BooleanItem = nssKey.set(flag)
}
