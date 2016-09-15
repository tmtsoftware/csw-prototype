package csw.examples.vslice.assembly

import akka.actor._
import csw.examples.vslice.assembly.BusyFSM.{AssemblyActivity, AssemblyState}
import csw.services.pkg.Assembly

/**
 * TMT Source Code: 6/11/16.
 */

object BusyFSM {

  sealed trait AssemblyState

  case object Ready extends AssemblyState

  case object Busy extends AssemblyState

  case object Error extends AssemblyState

  case object Continuous extends AssemblyState

  trait MessageData

  sealed trait AssemblyActivity

  case object Idle extends AssemblyActivity

  case object CommandAccepted extends AssemblyActivity

  case object CommandCompleted extends AssemblyActivity

  case object CommandError extends AssemblyActivity

  case object ContinuousCompleted extends AssemblyActivity

  case object ContinuousError extends AssemblyActivity

}

class BusyFSM(assembly: Assembly, name: String) extends FSM[AssemblyState, AssemblyActivity] {

  import BusyFSM._

  startWith(Ready, Idle)

  when(Ready) {
    case Event(CommandAccepted, md: MessageData) =>
      goto(Busy) using md
  }

  when(Busy) {
    case Event(CommandCompleted, md: MessageData) =>
      goto(Ready)
    case Event(CommandError, md: MessageData) =>
      goto(Ready)
  }

  def logState(nextState: AssemblyState, s: AssemblyState) = log.debug(s"In $stateName/$stateData going to $nextState/$s")

  def logTransition(message: String = "") = log.debug(s"On transition going from $stateName/$stateData to $nextStateData - $message")

  initialize()
}
