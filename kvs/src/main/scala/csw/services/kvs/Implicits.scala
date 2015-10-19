package csw.services.kvs

import akka.util.ByteString
import csw.services.kvs.KeyValueStore.KvsFormatter
import csw.util.cfg.Configurations.StateVariable.{ CurrentState, DemandState }
import csw.util.cfg.Configurations._
import csw.util.cfg.Events.{ SystemEvent, ObserveEvent, StatusEvent }
import scala.pickling.Defaults._
import scala.pickling.binary._
import redis.ByteStringDeserializerDefault

/**
 * Defines the automatic conversion to a ByteString and back again for commonly used value types
 */
trait Implicits extends ByteStringDeserializerDefault {
  implicit val statusEventKvsFormatter = new KvsFormatter[StatusEvent] {
    def serialize(e: StatusEvent): ByteString = {
      ByteString(e.pickle.value)
    }

    def deserialize(bs: ByteString): StatusEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[StatusEvent]
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

  implicit val systemEventKvsFormatter = new KvsFormatter[SystemEvent] {
    def serialize(t: SystemEvent): ByteString = {
      ByteString(t.pickle.value)
    }

    def deserialize(bs: ByteString): SystemEvent = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      ar.unpickle[SystemEvent]
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
