Key/Value Store and Publish/Subscribe
=====================================

This module provides key/value store and publish/subscribe features based on Redis (http://redis.io/).
An Event object (based on Akka's Config class) can be set or published on a channel and subscribers
can receive the events. The last n events are always saved for reference (where n is an optional argument).

Note that the tests assume the redis server is running.
