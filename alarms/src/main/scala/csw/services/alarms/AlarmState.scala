package csw.services.alarms

import AlarmState._
import akka.util.ByteString
import csw.services.alarms.AlarmModel.SeverityLevel
import redis.ByteStringDeserializer

/**
 * Types for attributes that are of interest to the Alarm Server only.
 */
object AlarmState {

  // Redis field names
  private[alarms] val acknowledgedStateField = "acknowledgedState"
  private[alarms] val latchedStateField = "latchedState"
  private[alarms] val latchedSeverityField = "latchedSeverityLevel"
  private[alarms] val shelvedStateField = "shelvedState"
  private[alarms] val activationStateField = "activationState"

  sealed trait AlarmStateItem {
    def name: String

    override def toString = name
  }

  /**
   * If the alarm severity is not Normal, and the alarm requires
   * acknowledgement, what is its current acknowledge state?
   */
  sealed trait AcknowledgedState extends AlarmStateItem

  object AcknowledgedState {

    case object NeedsAcknowledge extends AcknowledgedState {
      override val name = "NeedsAcknowledge"
    }

    case object Normal extends AcknowledgedState {
      override val name = "Normal"
    }

    def apply(s: String): AcknowledgedState = s match {
      case NeedsAcknowledge.name ⇒ NeedsAcknowledge
      case Normal.name           ⇒ Normal
    }
  }

  /**
   * If the alarm severity is not Normal, and the alarm should be latched, what
   * is the current latched state?
   */
  sealed trait LatchedState extends AlarmStateItem

  object LatchedState {

    object NeedsReset extends LatchedState {
      override val name = "NeedsReset"
    }

    object Normal extends LatchedState {
      override val name = "Normal"
    }

    def apply(s: String): LatchedState = {
      s match {
        case NeedsReset.name ⇒ NeedsReset
        case Normal.name     ⇒ Normal
      }
    }
  }

  /**
   * Is the alarm entity currently shelved?
   */
  sealed trait ShelvedState extends AlarmStateItem

  object ShelvedState {

    object Shelved extends ShelvedState {
      override val name = "Shelved"
    }

    object Normal extends ShelvedState {
      override val name = "Normal"
    }

    def apply(s: String): ShelvedState = s match {
      case Shelved.name ⇒ Shelved
      case Normal.name  ⇒ Normal
    }
  }

  /**
   * Is the alarm entity currently active or out of service. By default, an alarm
   * is active until placed out-of-service by the operator.
   */
  sealed trait ActivationState extends AlarmStateItem

  object ActivationState {

    object OutOfService extends ActivationState {
      override val name = "OutOfService"
    }

    object Normal extends ActivationState {
      override val name = "Normal"
    }

    def apply(s: String): ActivationState = s match {
      case OutOfService.name ⇒ OutOfService
      case Normal.name       ⇒ Normal
    }
  }

  /**
   * Creates an AlarmState from a map
   *
   * @param map a map as returned from AlarmState.asMap
   */
  def apply(map: Map[String, ByteString]): AlarmState = {
    val formatter = implicitly[ByteStringDeserializer[String]]
    AlarmState(
      AcknowledgedState(formatter.deserialize(map(acknowledgedStateField))),
      LatchedState(formatter.deserialize(map(latchedStateField))),
      SeverityLevel(formatter.deserialize(map(latchedSeverityField))).get,
      ShelvedState(formatter.deserialize(map(shelvedStateField))),
      ActivationState(formatter.deserialize(map(activationStateField)))
    )
  }
}

/**
 * Attributes that are of interest to the Alarm Server only
 */
case class AlarmState(
    acknowledgedState: AcknowledgedState = AcknowledgedState.Normal,
    latchedState:      LatchedState      = LatchedState.Normal,
    latchedSeverity:   SeverityLevel     = SeverityLevel.Disconnected,
    shelvedState:      ShelvedState      = ShelvedState.Normal,
    activationState:   ActivationState   = ActivationState.Normal
) {

  /**
   * @return The contents of this object as a map
   */
  def asMap(): Map[String, String] = Map(
    acknowledgedStateField → acknowledgedState.name,
    latchedStateField → latchedState.name,
    latchedSeverityField → latchedSeverity.name,
    shelvedStateField → shelvedState.name,
    activationStateField → activationState.name
  )
}