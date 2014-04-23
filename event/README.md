Event Service
=============

This projects implements an event service based on Akka and HornetQ.
Event service clients are assumed to be actors. There are two traits:
EventPublisher and EventSubscriber.

Event Publisher
---------------

The EventPublisher trait adds the method `publish(channel, event)`, where channel is some string
and event is a type of Configuration, a class based on the Akka Config class.
By convention, the channel should be the same as the main key in the event,
for example: `tmt.mobie.red.dat.exposureInfo`.

Event Subscriber
----------------

The EventSubscriber trait adds the method `subscribe(channel)`. After calling this method, the actor
will receive all Event messages published on the channel. You can use wildcards in the channel string.
For example `tmt.mobie.red.dat.*` or `tmt.mobie.red.dat.#`, where `*` matches a single word and `#` matches
multiple words separated by dots. Subscribers should be careful not to block when receiving messages,
so that the actor queue does not fill up too much.
