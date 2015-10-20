package csw.services.kvs

import akka.util.ByteString
import csw.services.kvs.KeyValueStore.KvsFormatter
import csw.util.cfg.Configurations.StateVariable.{ CurrentState, DemandState }
import csw.util.cfg.Configurations._
import csw.util.cfg.Events._
import redis.ByteStringDeserializerDefault

import csw.util.cfg.ConfigSerializer._

/**
 * Defines the automatic conversion to a ByteString and back again for commonly used value types
 */
trait Implicits extends ByteStringDeserializerDefault {
  implicit val statusEventKvsFormatter = new KvsFormatter[StatusEvent] {
    def serialize(e: StatusEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): StatusEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[StatusEvent](ar)
    }
  }

  implicit val observeEventKvsFormatter = new KvsFormatter[ObserveEvent] {
    def serialize(e: ObserveEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ObserveEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ObserveEvent](ar)
    }
  }

  implicit val systemEventKvsFormatter = new KvsFormatter[SystemEvent] {
    def serialize(e: SystemEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SystemEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SystemEvent](ar)
    }
  }

  implicit val demandStateKvsFormatter = new KvsFormatter[DemandState] {
    def serialize(e: DemandState): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): DemandState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[DemandState](ar)
    }
  }

  implicit val currentStateKvsFormatter = new KvsFormatter[CurrentState] {
    def serialize(e: CurrentState): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): CurrentState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[CurrentState](ar)
    }
  }

  implicit val setupConfigKvsFormatter = new KvsFormatter[SetupConfig] {
    def serialize(e: SetupConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SetupConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SetupConfig](ar)
    }
  }

  implicit val setupConfigArgKvsFormatter = new KvsFormatter[SetupConfigArg] {
    def serialize(e: SetupConfigArg): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SetupConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SetupConfigArg](ar)
    }
  }

  implicit val eventTypeKvsFormatter = new KvsFormatter[EventServiceEvent] {
    def serialize(e: EventServiceEvent): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): EventServiceEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[EventServiceEvent](ar)
    }
  }

  implicit val sequenceConfigKvsFormatter = new KvsFormatter[SequenceConfig] {
    def serialize(e: SequenceConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SequenceConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SequenceConfig](ar)
    }
  }

  implicit val controlConfigKvsFormatter = new KvsFormatter[ControlConfig] {
    def serialize(e: ControlConfig): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): ControlConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[ControlConfig](ar)
    }
  }

  implicit val sequenceConfigArgKvsFormatter = new KvsFormatter[SequenceConfigArg] {
    def serialize(e: SequenceConfigArg): ByteString = {
      ByteString(write(e))
    }

    def deserialize(bs: ByteString): SequenceConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      read[SequenceConfigArg](ar)
    }
  }

  implicit val controlConfigArgKvsFormatter = new KvsFormatter[ControlConfigArg] {
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
