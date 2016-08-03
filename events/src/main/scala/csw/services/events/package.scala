package csw.services

import csw.util.config.Events.EventServiceEvent

/**
 * == Event Service ==
 *
 * This module provides an Event Service based on [[http://redis.io/ Redis]].
 *
 * An Event object can be set or published on a channel and subscribers
 * receive the events. The last ''n'' events are saved for reference (where n is an optional argument).
 *
 * Note that the tests assume the redis server is running.
 *
 * The [[csw.services.events.EventService]] return values as futures, to avoid blocking on network access.
 * For convenience, there is also a [[csw.services.events.BlockingEventService]] wrapper that waits for
 * completion before returning.
 */
package object events {
  /**
   * An Event here is a system or observe event
   */
  type Event = EventServiceEvent

}

