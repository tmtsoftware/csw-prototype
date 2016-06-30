package csw.services.alarms

import AlarmState._

object AlarmState {

  sealed trait AcknowledgedState

  object AcknowledgedState {

    case object NeedsAcknowledge extends AcknowledgedState

    case object Normal extends AcknowledgedState

  }

  sealed trait LatchedState

  object LatchedState {

    object NeedsReset extends LatchedState

    object Normal extends LatchedState

  }

  sealed trait ShelvedState

  object ShelvedState {

    object Shelved extends ShelvedState

    object Normal extends ShelvedState

  }

  sealed trait ActivationState

  object ActivationState {

    object OutOfService extends ActivationState

    object Normal extends ActivationState

  }

}

case class AlarmState(acknowledgedState: AcknowledgedState, latchedState: LatchedState,
                      shelvedState: ShelvedState, activationState: ActivationState) {

}