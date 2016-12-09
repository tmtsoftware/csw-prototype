package csw.examples.vslice.seq

import akka.util.Timeout
import csw.util.config.Configurations
import csw.util.config.Configurations.{ConfigKey, SetupConfig, SetupConfigArg}
import csw.services.ccs.BlockingAssemblyClient

import scala.concurrent.duration._

/**
 * TMT Source Code: 12/4/16.
 */
object Demo {
  //  import SeqSupport._

  import csw.services.sequencer.SequencerEnv._

  implicit val timeout = Timeout(10.seconds)

  val taName = "lgsTrombone"
  val thName = "lgsTromboneHCD"

  val componentPrefix: String = "nfiraos.ncc.trombone"

  // Public command configurations
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val initCK: ConfigKey = initPrefix

  // Datum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK: ConfigKey = datumPrefix

  val sca1: SetupConfigArg = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

  def getTrombone: BlockingAssemblyClient = resolveAssembly(taName)

  // Need to add subscribing to a SystemEvent and subscribing to StatusEvents so a command to look up each of the services
  // is needed and something to subscribe maybe.

}
