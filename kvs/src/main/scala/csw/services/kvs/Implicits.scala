package csw.services.kvs

import akka.util.ByteString
import csw.util.config.Configurations.SetupConfig
import csw.util.config.Events.{ObserveEvent, TelemetryEvent}
import csw.util.config.StateVariable.{CurrentState, DemandState}
import redis.ByteStringFormatter
import scala.pickling.Defaults._
import scala.pickling.binary._

/**
 * Defines the automatic conversion to a ByteString and back again for commonly used value types
 */
trait Implicits {
  implicit val telemetryEventByteStringFormatter = new ByteStringFormatter[TelemetryEvent] {
    def serialize(t: TelemetryEvent): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): TelemetryEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[TelemetryEvent]
    }
  }

  implicit val observeEventByteStringFormatter = new ByteStringFormatter[ObserveEvent] {
    def serialize(t: ObserveEvent): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): ObserveEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[ObserveEvent]
    }
  }


  implicit val demandStateByteStringFormatter = new ByteStringFormatter[DemandState] {
    def serialize(t: DemandState): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): DemandState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[DemandState]
    }
  }


  implicit val currentStateByteStringFormatter = new ByteStringFormatter[CurrentState] {
    def serialize(t: CurrentState): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): CurrentState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[CurrentState]
    }
  }


  implicit val setupConfigByteStringFormatter = new ByteStringFormatter[SetupConfig] {
    def serialize(t: SetupConfig): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): SetupConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[SetupConfig]
    }
  }

}
