Key/Value Store and Publish/Subscribe
=====================================

This module provides key/value store and publish/subscribe features based on Redis (http://redis.io/).
An event or configuration object can be set or published on a channel and subscribers
can receive the events. The last n events are always saved for reference (where n is an optional argument).

Note that the tests assume the redis server is running. The host and port for the Redis instance can be configured
in the application [config file](src/main/resources/reference.conf), or you can use the
[trackLocation](https://github.com/tmtsoftware/csw/tree/master/apps/trackLocation) application to register a
Redis instance with the location service and then look up the host and port to use for it.

Telemetry Service
-----------------

The [Telemetry Service](src/main/scala/csw/services/kvs/TelemetryService.scala) defined in this project uses a
`KeyValueStore` containing `StatusEvents`.
The API is slightly simpler than the generic `KeyValueStore`, since the type is known
(Keys and not required, since it uses the event's prefix as a key).

See the kvs [unit tests](src/test/scala/csw/services/kvs) for some examples of the usage in Scala
(Or [here](https://github.com/tmtsoftware/csw/tree/master/javacsw/src/test/java/javacsw/services/kvs)
for the Java versions).
