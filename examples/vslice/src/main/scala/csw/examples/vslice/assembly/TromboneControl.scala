package csw.examples.vslice.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.examples.vslice.assembly.TromboneAssembly.UpdateTromboneHCD
import csw.examples.vslice.assembly.TromboneControl.GoToStagePosition
import csw.examples.vslice.hcd.TromboneHCD
import csw.services.ccs.HcdController.Submit
import csw.util.config.DoubleItem

/**
 * An actor dedicated to converting stage position values to encoder units and writing in a oneway fashion to
 * the trombone HCD.
 *
 * Other actors, primarily the FollowActor write stage positions with units of millimeters. This actor uses the
 * function in algorithms to convert this to encoder units. It then uses the Submit command of CCS to send the
 * SetupConfig to the trombone HCD.
 *
 * Note that the actor receive method is parameterized with an optional HCD actor ref. It is set initially when
 * the actor is created and may be updated if the actor goes down or up. The actor ref is an [[scala.Option]] so
 * that if the actor ref is set to None, no message will be sent, but the actor can operator normally.
 *
 * @param ac the trombone AssemblyContext contains important shared values and useful function
 * @param tromboneHCDIn the actor reference to the trombone HCD as a [[scala.Option]]
 */
class TromboneControl(ac: AssemblyContext, tromboneHCDIn: Option[ActorRef]) extends Actor with ActorLogging {

  log.info(s"TromboneIn: ========> $tromboneHCDIn")
  def receive = controlReceive(tromboneHCDIn)

  def controlReceive(tromboneHCD: Option[ActorRef]): Receive = {
    case GoToStagePosition(newPosition) =>

      // It should be correct, but check
      assert(newPosition.units == ac.stagePositionUnits)

      // Convert to encoder units
      val encoderPosition = Algorithms.stagePositionToEncoder(ac.controlConfig, newPosition.head)

      // Final check before sending off to hardware
      log.info(s"epos: $encoderPosition, minLimit: ${ac.controlConfig.minEncoderLimit}, maxEnc: ${ac.controlConfig.maxEncoderLimit}")
      assert(encoderPosition > ac.controlConfig.minEncoderLimit && encoderPosition < ac.controlConfig.maxEncoderLimit)

      log.debug(s"Setting trombone axis to stage position: ${newPosition.head} and encoder: $encoderPosition")

      // Send command to HCD here
      tromboneHCD.foreach(_ ! Submit(TromboneHCD.positionSC(encoderPosition)))

    case UpdateTromboneHCD(tromboneHCDUpdate) =>
      context.become(controlReceive(tromboneHCDUpdate))

    case x => log.error(s"Unexpected message received in TromboneControl:controlReceive: $x")
  }

}

object TromboneControl {
  // Props for creating the TromboneControl actor
  def props(assemblyContext: AssemblyContext, tromboneHCD: Option[ActorRef] = None) = Props(classOf[TromboneControl], assemblyContext, tromboneHCD)

  // Used to send a position that requries transformaton from
  case class GoToStagePosition(stagePosition: DoubleItem)

}
