Event Service Java API
======================

This project implements an event service based on Akka and HornetQ.

Event Publisher
---------------

The [JEventService](src/main/java/javacsw/services/event/JEventService.java) class
provides the method `publish(event)` to publish an
object of type EventServiceEvent (base trait/interface for events).

Event Subscriber
----------------

The abstract [JEventSubscriber](src/main/scala/javacsw/services/event/JEventSubscriber.scala) class adds the
method `subscribe(prefix)`.
After calling this method, the subscribing actor will receive all Event messages published for the prefix.
You can use wildcards in the prefix string.
For example `tmt.mobie.red.dat.*` or `tmt.mobie.red.dat.#`, where * matches a single word and # matches
multiple words separated by dots. Subscribers should be careful not to block when receiving messages,
so that the actor queue does not fill up too much.

Example Java based Event Subscriber
-----------------------------------

```
    class Subscriber extends JEventSubscriber {
        LoggingAdapter log = Logging.getLogger(getContext().system(), this);

        public static Props props() {
            return Props.create(new Creator<Subscriber>() {
                private static final long serialVersionUID = 1L;

                @Override
                public Subscriber create() throws Exception {
                    return new Subscriber();
                }
            });
        }

        public Subscriber() {
            getContext().setReceiveTimeout(timeout);
            subscribe(prefix);
            receive(ReceiveBuilder.
                    match(ObserveEvent.class, e -> receivedObserveEvent(e)).
                    match(ReceiveTimeout.class, t -> receiveTimedOut()).
                    matchAny(t -> log.warning("Unknown message received: " + t)).
                    build();
        }

        private void receiveTimedOut() {
            log.error("Publisher seems to be blocked!");
            getContext().system().terminate();
        }

        private void receivedObserveEvent(ObserveEvent event) {
            int numOpt = event.jvalue(eventNum);
            // code to handle observe event ...
        }
    }
```
