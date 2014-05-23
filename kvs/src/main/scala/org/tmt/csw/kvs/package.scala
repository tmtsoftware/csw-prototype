package org.tmt.csw

import org.tmt.csw.util.Configuration
import redis.ByteStringFormatter
import akka.util.ByteString

package object kvs {

  /**
   * An Event here is just a Configuration with an implicit conversion to/from ByteString for
   * use with Redis
   */
  type Event = Configuration

  /**
   * Defines the automatic conversion of an Event to a ByteString and back again.
   */
  implicit val byteStringFormatter = new ByteStringFormatter[Event] {
    def serialize(event: Event): ByteString = {
      ByteString(event.toString) // Uses the simplified JSON format
    }

    def deserialize(bs: ByteString): Event = {
      Configuration(bs.utf8String)
    }
  }

}

