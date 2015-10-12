package csw.services.kvs

import akka.util.ByteString
import csw.services.kvs.KeyValueStore.KvsFormatter
import csw.util.config.Configurations.{ SetupConfigArg, SetupConfig }
import csw.util.config.Events.{ ObserveEvent, TelemetryEvent }
import csw.util.config.StateVariable.{ CurrentState, DemandState }
import scala.pickling.Defaults._
import scala.pickling.binary._

/**
 * Defines the automatic conversion to a ByteString and back again for commonly used value types
 */
trait Implicits {
  implicit val telemetryEventKvsFormatter = new KvsFormatter[TelemetryEvent] {
    def serialize(t: TelemetryEvent): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): TelemetryEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[TelemetryEvent]
    }
  }

  implicit val observeEventKvsFormatter = new KvsFormatter[ObserveEvent] {
    def serialize(t: ObserveEvent): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): ObserveEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[ObserveEvent]
    }
  }

  implicit val demandStateKvsFormatter = new KvsFormatter[DemandState] {
    def serialize(t: DemandState): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): DemandState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[DemandState]
    }
  }

  implicit val currentStateKvsFormatter = new KvsFormatter[CurrentState] {
    def serialize(t: CurrentState): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): CurrentState = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[CurrentState]
    }
  }

  implicit val setupConfigKvsFormatter = new KvsFormatter[SetupConfig] {
    def serialize(t: SetupConfig): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): SetupConfig = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[SetupConfig]
    }
  }

  implicit val setupConfigArgKvsFormatter = new KvsFormatter[SetupConfigArg] {
    def serialize(t: SetupConfigArg): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): SetupConfigArg = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[SetupConfigArg]
    }
  }

}

/**
 * Use import csw.services.kvs.Implicits._ to get the implicit definitions in scope.
 */
object Implicits extends Implicits
