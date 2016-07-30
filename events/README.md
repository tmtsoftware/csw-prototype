Event Service, Key/Value Store and Publish/Subscribe
====================================================

This module provides event service, key/value store and publish/subscribe features based on [Redis](http://redis.io/).
An event or configuration object can be set or published on a channel and subscribers
can receive the events. The last n events are always saved for reference (where n is an optional argument).

Note that the tests assume the redis server is running. The host and port for the Redis instance can be configured
in the application [config file](src/main/resources/reference.conf), or you can use the
[trackLocation](https://github.com/tmtsoftware/csw/tree/master/apps/trackLocation) application to register a
Redis instance with the location service and then look up the host and port to use for it.

Telemetry Service
-----------------

The [Telemetry Service](src/main/scala/csw/services/events/TelemetryService.scala) defined in this project uses an
[EventService](src/main/scala/csw/services/events/EventService.scala) containing `StatusEvents`.
The API is slightly simpler than the generic `EventService`, since the type is known
(Keys and not required, since it uses the event's prefix as a key).

See the [unit tests](src/test/scala/csw/services/events) for some examples of the usage in Scala
(Or [here]../javacsw/src/test/java/javacsw/services/events)
for the Java versions).
