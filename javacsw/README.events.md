Java API for the Event Service
==============================

See the [events](../events) project for an overview of the Scala API.

This module implements an Event Service based on [Redis](http://redis.io/).
An event can be published and subscribers can receive the events. 
The last n events are always saved for reference (where n is an optional argument).

Note that the tests assume the redis server is running. The host and port for the Redis instance can be configured
in the application [config file](src/main/resources/reference.conf), or you can use the
[trackLocation](https://github.com/tmtsoftware/csw-prototype/tree/master/apps/trackLocation) application to register a
Redis instance with the location service and then look up the host and port to use for it.

The [IBlockingEventService](src/main/java/javacsw/services/events/IBlockingEventService.java) interface 
provides factory methods to create an event service instance. This API blocks while waiting for replies from
the server.

The [IEventService](src/main/java/javacsw/services/events/IEventService.java) interface also provides factory 
methods to create an event service instance, but provides a non-blocking API based on futures.

Example Event Publisher (blocking)
----------------------------------

The example below creates an event (event1), publishes it, then gets the latest event from the event service
and compares it to the original event:

```java
    String prefix1 = "tcs.test1";
    StatusEvent event1 = StatusEvent(prefix1)
      .add(jset(infoValue, 1))
      .add(jset(infoStr, "info 1"));

    eventService.publish(event1);
    StatusEvent event = (StatusEvent) eventService.get(prefix1).get();
    assertEquals(event.prefix(), prefix1);
    assertEquals(event.get(infoValue).get().head(), 1);
    assertEquals(event.get(infoStr).get().head(), "info 1");
```

Example Event Subscriber (callback method)
-------------------------------------------

The EventService.subscribe method takes an optional ActorRef of an arbitrary actor and an optional callback function to be called 
when an event matching the given prefixes is received. In the example below, we only provide the callback
argument value. You could also provide an ActorRef of some actor that should receive the Event message.


```java
  // Called when an event is received
  static IEventService.EventHandler eventHandler = ev -> eventReceived = logger.info("Listener received event: " + ev);

  EventMonitor monitor = eventService.subscribe(Optional.empty(), Optional.of(eventHandler), prefix);  
```

Example Event Subscriber Actor
------------------------------

In the example below, a we create an actor that will receive Event messages that match the 
prefixes (prefix1, prefix2) in the call to subscribe. 

The `JAbstractSubscriber` class uses the values
in the config file (resources/reference.conf or resources/application.conf) to open a connection
to the Redis server.

```java
    static class MySubscriber extends JAbstractSubscriber {
        private final String prefix1;
        private final String prefix2;

        private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        public static Props props(String prefix1, String prefix2) {
            return Props.create(new Creator<MySubscriber>() {
                private static final long serialVersionUID = 1L;

                @Override
                public MySubscriber create() throws Exception {
                    return new MySubscriber(prefix1, prefix2);
                }
            });
        }

        public MySubscriber(String prefix1, String prefix2) {
            this.prefix1 = prefix1;
            this.prefix2 = prefix2;
            
            subscribe(prefix1, prefix2);

            receive(ReceiveBuilder
              .match(StatusEvent.class, this::handleStatusEvent)
              .matchAny(t -> log.warning("Unexpected message: " + t)
              ).build());
        }

        private void handleStatusEvent(StatusEvent event) {
            if (event.prefix().equals(prefix1)) {
              // handle event1 ...
            } else if (event.prefix().equals(prefix2)) {
              // handle event2 ...
            }
        }
    }
```
