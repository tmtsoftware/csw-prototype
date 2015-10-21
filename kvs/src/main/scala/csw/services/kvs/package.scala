package csw.services

import csw.util.cfg.Configurations.SetupConfig

package object kvs {
  import Implicits.setupConfigKvsFormatter
  type TelemetryService = BlockingKeyValueStore[SetupConfig]
}

