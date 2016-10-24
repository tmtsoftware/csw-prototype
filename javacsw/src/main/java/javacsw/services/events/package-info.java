/**
 * Java API for the Event Service.
 * <p>
 *  For the Scala API see {@link csw.services.events.EventService}.
 * <p>
 *  This module implements an Event Service based on <a href="http://redis.io/">Redis</a>.
 *  An event can be published and subscribers can receive the events.
 *  The last n events are always saved for reference (where n is an optional argument).
 * <p>
 *  Note that the tests assume the redis server is running. The host and port for the Redis instance can be configured
 *  in the application config file (reference.conf, application.conf), or you can use the
 *  trackLocation application to register a
 *  Redis instance with the location service and then look up the host and port to use for it.
 * <p>
 *  The {@link javacsw.services.events.IEventService} interface also provides factory
 *  methods to create an event service instance, but provides a non-blocking API based on futures.
 * <p>
 *  <strong>Example Event Publisher (blocking)</strong>
 * <p>
 *  The example below creates an event (event1), publishes it, then gets the latest event from the event service
 *  and compares it to the original event:
 * <p>
 * <pre> {@code
 *  String prefix1 = "tcs.test1";
 *  StatusEvent event1 = StatusEvent(prefix1)
 *  .add(jset(infoValue, 1))
 *  .add(jset(infoStr, "info 1"));
 *
 *  eventService.publish(event1);
 *  StatusEvent event = (StatusEvent) eventService.get(prefix1).get();
 *  assertEquals(event.prefix(), prefix1);
 *  assertEquals(event.get(infoValue).get().head(), 1);
 *  assertEquals(event.get(infoStr).get().head(), "info 1");
 * } </pre>
 * <p>
 *  <strong>Example Event Subscriber (callback method)</strong>
 * <p>
 *  The EventService.subscribe method takes an ActorRef of an arbitrary actor and a callback function to be called
 *  when an event matching the given prefixes is received. In the example below, we only provide the callback
 *  argument value. You could also provide an ActorRef of some actor that should receive the Event message.
 * <p>
 * <pre> {@code
 *  // Called when an event is received
 *  static IEventService.EventHandler eventHandler = ev -> eventReceived = logger.info("Listener received event: " + ev);
 *
 *  EventMonitor monitor = eventService.subscribe(Optional.empty(), Optional.of(eventHandler), prefix);
 * } </pre>
 * <p>
 *  <strong>Example Event Subscriber Actor</strong>
 * <p>
 *  In the example below, a we create an actor that will receive Event messages that match the
 *  prefixes (prefix1, prefix2) in the call to subscribe.
 * <p>
 *  The `JAbstractSubscriber` class uses the values
 *  in the config file (resources/reference.conf or resources/application.conf) to open a connection
 *  to the Redis server.
 * <p>
 * <pre> {@code
 *  static class MySubscriber extends JAbstractSubscriber {
 *  private final String prefix1;
 *  private final String prefix2;
 *
 *  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
 *
 *  public static Props props(String prefix1, String prefix2) {
 *  return Props.create(new Creator<MySubscriber>() {
 *  private static final long serialVersionUID = 1L;
 *
 *  @Override
 *  public MySubscriber create() throws Exception {
 *  return new MySubscriber(prefix1, prefix2);
 *  }
 *  });
 *  }
 *
 *  public MySubscriber(String prefix1, String prefix2) {
 *  this.prefix1 = prefix1;
 *  this.prefix2 = prefix2;
 *
 *  subscribe(prefix1, prefix2);
 *
 *  receive(ReceiveBuilder
 *  .match(StatusEvent.class, this::handleStatusEvent)
 *  .matchAny(t -> log.warning("Unexpected message: " + t)
 *  ).build());
 *  }
 *
 *  private void handleStatusEvent(StatusEvent event) {
 *  if (event.prefix().equals(prefix1)) {
 *  // handle event1 ...
 *  } else if (event.prefix().equals(prefix2)) {
 *  // handle event2 ...
 *  }
 *  }
 *  }
 * } </pre>
 */
package javacsw.services.events;