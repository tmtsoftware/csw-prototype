package csw.services

import csw.util.param.Events.EventServiceEvent

/**
 * == Event Service ==
 *
 * This module implements an Event Service based on [[http://redis.io/ Redis]].
 * An event can be published and subscribers can receive the events.
 * The last n events are always saved for reference (where n is an optional argument).
 *
 * Note that the tests assume the redis server is running. The host and port for the Redis instance can be configured
 * in the application config file, or you can use the `trackLocation` application to register a
 * Redis instance with the location service and then look up the host and port to use for it.
 *
 * === Telemetry Service ===
 *
 * The Telemetry Service uses an EventService that deals only with StatusEvent objects.
 * The API is *slightly* simpler than the generic `EventService`, since the concrete event type is known.
 *
 * See the unit tests for some examples of the usage in Scala and Java.
 *
 * === Blocking and Non-Blocking Versions ===
 *
 * For convenience, blocking and non-blocking APIs are provided. The base implementation is non-blocking
 * (based on Akka actors). In some cases, it may be more convenient to use the simpler, blocking API.
 *
 * === Example Event Publisher (blocking) ===
 *
 * {{{
 *   val settings = EventServiceSettings(system)
 *   val eventService = BlockingEventService(5.seconds, settings)
 *
 *   val event1 = StatusEvent("tcs.test1")
 *       .add(infoValue.set(1))
 *       .add(infoStr.set("info 1"))
 *
 *   eventService.publish(event1)
 *   eventService.get(event1.prefix).get match {
 *       case event: StatusEvent =>
 *         assert(event.prefix == event1.prefix)
 *         assert(event(infoValue).head == 1)
 *         assert(event(infoStr).head == "info 1")
 *       case _ => fail("Expected a StatusEvent")
 *   }
 * }}}
 *
 * === Example Event Subscriber (callback method) ===
 *
 * The EventService.subscribe method takes an ActorRef of an arbitrary actor or a callback function to be called
 * when an event matching the given prefixes is received. In the example below, we only provide the callback
 * argument value. You could also provide an ActorRef of some actor that should receive the Event message.
 *
 *
 * {{{
 *      def listener(ev: Event): Unit = {
 *        // ...
 *      }
 *
 *      val monitor = eventService.subscribe(None, Some(listener), prefix)
 * }}}
 *
 * === Example Event Subscriber Actor ===
 *
 * In the example below, a we create an actor that will receive Event messages that match the
 * prefixes (prefix1, prefix2) in the call to subscribe.
 *
 * The EventSubscriber class uses the values
 * in the config file (resources/reference.conf or resources/application.conf) to open a connection
 * to the Redis server.
 *
 * {{{
 * class MySubscriber(prefix1: String, prefix2: String) extends EventSubscriber {
 *
 *   subscribe(prefix1, prefix2)
 *
 *   def receive: Receive = {
 *     case event: Event =>   ...
 *   }
 * }
 * }}}
 */
package object events {
  /**
   * An Event here is a system or observe event
   */
  type Event = EventServiceEvent

}

