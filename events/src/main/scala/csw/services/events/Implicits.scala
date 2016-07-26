package csw.services.events

import akka.util.ByteString
import csw.services.events.EventService.EventFormatter
import csw.util.config.StateVariable.{CurrentState, DemandState}
import csw.util.config.Configurations._
import csw.util.config.Events._
import redis.ByteStringDeserializerDefault
import csw.util.config.ConfigSerializer._
import csw.util.config.StateVariable._

/**
 * Defines the automatic conversion to a ByteString and back again for commonly used value types
 */
trait Implicits extends ByteStringDeserializerDefault {
  implicit val statusEventKvsFormatter = new EventFormatter[StatusEvent] {
    def serialize(e: StatusEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): StatusEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[StatusEvent](ar)
    }
  }

  implicit val observeEventKvsFormatter = new EventFormatter[ObserveEvent] {
    def serialize(e: ObserveEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ObserveEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ObserveEvent](ar)
    }
  }

  implicit val systemEventKvsFormatter = new EventFormatter[SystemEvent] {
    def serialize(e: SystemEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SystemEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SystemEvent](ar)
    }
  }

  implicit val setupConfigKvsFormatter = new EventFormatter[SetupConfig] {
    def serialize(e: SetupConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SetupConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SetupConfig](ar)
    }
  }

  implicit val observeConfigKvsFormatter = new EventFormatter[ObserveConfig] {
    def serialize(e: ObserveConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ObserveConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ObserveConfig](ar)
    }
  }

  implicit val currentStateKvsFormatter = new EventFormatter[CurrentState] {
    def serialize(e: CurrentState): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): CurrentState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[CurrentState](ar)
    }
  }

  implicit val demandStateKvsFormatter = new EventFormatter[DemandState] {
    def serialize(e: DemandState): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): DemandState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[DemandState](ar)
    }
  }

  implicit val stateVariableKvsFormatter = new EventFormatter[StateVariable] {
    def serialize(e: StateVariable): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): StateVariable = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[StateVariable](ar)
    }
  }

  implicit val setupConfigArgKvsFormatter = new EventFormatter[SetupConfigArg] {
    def serialize(e: SetupConfigArg): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SetupConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SetupConfigArg](ar)
    }
  }

  implicit val observeConfigArgKvsFormatter = new EventFormatter[ObserveConfigArg] {
    def serialize(e: ObserveConfigArg): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ObserveConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ObserveConfigArg](ar)
    }
  }

  implicit val eventTypeKvsFormatter = new EventFormatter[EventServiceEvent] {
    def serialize(e: EventServiceEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): EventServiceEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[EventServiceEvent](ar)
    }
  }

  implicit val sequenceConfigKvsFormatter = new EventFormatter[SequenceConfig] {
    def serialize(e: SequenceConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SequenceConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SequenceConfig](ar)
    }
  }

  implicit val controlConfigKvsFormatter = new EventFormatter[ControlConfig] {
    def serialize(e: ControlConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ControlConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ControlConfig](ar)
    }
  }

  implicit val sequenceConfigArgKvsFormatter = new EventFormatter[SequenceConfigArg] {
    def serialize(e: SequenceConfigArg): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SequenceConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SequenceConfigArg](ar)
    }
  }

  implicit val controlConfigArgKvsFormatter = new EventFormatter[ControlConfigArg] {
    def serialize(e: ControlConfigArg): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ControlConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ControlConfigArg](ar)
    }
  }
}

/**
 * Use import csw.services.kvs.Implicits._ to get the implicit definitions in scope.
 */
object Implicits extends Implicits
