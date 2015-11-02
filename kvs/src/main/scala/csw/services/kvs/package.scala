package csw.services

import akka.actor.ActorRefFactory
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.duration.Duration

/**
 * A [[csw.services.kvs.KeyValueStore]] stores values of a given type (T) under string keys.
 * The type T needs to have an implicit conversion to the [[csw.services.kvs.KeyValueStore.KvsFormatter]] trait,
 * so that the values can be serialized and set to the Redis server.
 *
 * The [[csw.services.kvs.Implicits]] trait and object provide a number of commonly used
 * implicit definitions that include the config classes from the util project.
 *
 * The [[csw.services.kvs.KeyValueStore]] return values use futures, to avoid blocking on network access.
 * For convenience, there is also a [[csw.services.kvs.BlockingKeyValueStore]] wrapper that waits for
 * completion before returning.
 */
package object kvs {
  import Implicits.setupConfigKvsFormatter
  type TelemetryService = BlockingKeyValueStore[SetupConfig]

  object TelemetryService {
    def apply(timeout: Duration, settings: KvsSettings)(implicit _system: ActorRefFactory): TelemetryService = new TelemetryService(timeout, settings)
  }
}

