package csw.services

import akka.actor.ActorRefFactory
import csw.util.config.Configurations.SetupConfig
import csw.util.config.Events.StatusEvent

import scala.concurrent.duration.Duration

/**
 * == Key/Value Store and Publish/Subscribe ==
 *
 * This module provides key/value store and publish/subscribe features based on [[http://redis.io/ Redis]].
 * An Event object can be set or published on a channel and subscribers
 * receive the events. The last ''n'' events are saved for reference (where n is an optional argument).
 *
 * Note that the tests assume the redis server is running.
 *
 * A [[csw.services.events.KeyValueStore]] stores values of a given type (T) under string keys.
 * The type T needs to have an implicit conversion to the [[csw.services.events.KeyValueStore.KvsFormatter]] trait,
 * so that the values can be serialized and set to the Redis server and later deserialized.
 *
 * The [[csw.services.events.Implicits]] trait and object provide a number of commonly used
 * implicit definitions that include the configuration classes from the util project.
 *
 * The [[csw.services.events.KeyValueStore]] return values as futures, to avoid blocking on network access.
 * For convenience, there is also a [[csw.services.events.BlockingKeyValueStore]] wrapper that waits for
 * completion before returning.
 */
package object events {
}

