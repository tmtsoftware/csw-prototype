package csw.services

import akka.actor.ActorRefFactory
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.duration.Duration

package object kvs {
  import Implicits.setupConfigKvsFormatter
  type TelemetryService = BlockingKeyValueStore[SetupConfig]

  object TelemetryService {
    def apply(timeout: Duration, settings: KvsSettings)(implicit _system: ActorRefFactory): TelemetryService = new TelemetryService(timeout, settings)
  }
}

