package csw.services

import csw.util.cfg.Events.EventServiceEvent

/**
  * == Event Publisher ==
  *
  * The EventService class provides the method ''publish(event)'' to publish an object of type ''Event''
  * (a type alias for ''EventServiceEvent'').
  *
  * Example:
  * {{{
  *  val settings = EventServiceSettings(context.system)
  *  val eventService = EventService(prefix, settings)
  *  val event = ObserveEvent(prefix)
  *    .set(eventNum, num)
  *    .set(exposureTime, 1.0)
  *    .set(imageData, testImageData)
  *  eventService.publish(event)
  * }}}
  *
  * == Event Subscriber ==
  *
  * The EventSubscriber trait adds the method ''subscribe(prefix)''. After calling this method, the actor
  * will receive all Event messages published for the prefix. You can use wildcards in the prefix string.
  * For example ''tmt.mobie.red.dat.*'' or ''tmt.mobie.red.dat.#'', where ''*'' matches a single word and ''#'' matches
  * multiple words separated by dots. Subscribers should be careful not to block when receiving messages,
  * so that the actor queue does not fill up too much.
  *
  * Example:
  * {{{
  *   class Subscriber extends Actor with ActorLogging with EventSubscriber {
  *     subscribe(prefix)
  *
  *     override def receive: Receive = {
  *       case event: ObserveEvent ⇒  log.info(s"Received event: \$event")
  *     }
  *   }
  * }}}
  */
package object event {

  /**
    * An Event here is a system or observe event
    */
  type Event = EventServiceEvent

}

