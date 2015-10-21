package csw.services

import akka.actor.ActorSystem
import csw.util.cfg.Configurations.SetupConfig

import scala.concurrent.duration.Duration

package object kvs {
  import Implicits.setupConfigKvsFormatter
  type TelemetryService = BlockingKeyValueStore[SetupConfig]

  object TelemetryService {
    def apply(timeout: Duration)(implicit system: ActorSystem): TelemetryService = new TelemetryService(timeout)
  }
}

