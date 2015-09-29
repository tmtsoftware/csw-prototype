package csw.services

import csw.util.cfg_old.Events.EventType
import redis.ByteStringFormatter
import akka.util.ByteString

package object kvs {

  /**
   * An Event here is an EventType instance with an implicit conversion to/from ByteString for
   * use with Redis
   */
  type Event = EventType

  /**
   * Defines the automatic conversion of an Event to a ByteString and back again.
   */
  implicit val byteStringFormatter = new ByteStringFormatter[Event] {
    def serialize(event: Event): ByteString = {
      ByteString(event.toBinary)
    }

    def deserialize(bs: ByteString): Event = {
      val ar = Array.ofDim[Byte](bs.length)
      bs.asByteBuffer.get(ar)
      EventType(ar)
    }
  }

}

