package csw.examples.vslice.seq

import akka.util.Timeout
import csw.util.config.{Configurations, DoubleKey}
import csw.util.config.Configurations.{ConfigKey, SetupConfig, SetupConfigArg}
import csw.services.ccs.BlockingAssemblyClient
import csw.util.config.UnitsOfMeasure.kilometers

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


  val naRangeDistanceKey = DoubleKey("rangeDistance")
  val naRangeDistanceUnits = kilometers

  // Position submit command
  val positionPrefix = s"$componentPrefix.position"
  val positionCK: ConfigKey = positionPrefix

  def positionSC(rangeDistance: Double): SetupConfig = SetupConfig(positionCK).add(naRangeDistanceKey -> rangeDistance withUnits naRangeDistanceUnits)


  val sca1 = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))

  // This will send a config arg with 10 position commands
  val testRangeDistance = 90 to 130 by 10
  val positionConfigs = testRangeDistance.map(f => positionSC(f))

  val sca2 = Configurations.createSetupConfigArg("testObsId", positionSC(100.0))

  val sca3 = Configurations.createSetupConfigArg("testObsId", positionConfigs: _*)

  def getTrombone: BlockingAssemblyClient = resolveAssembly(taName)

  // Need to add subscribing to a SystemEvent and subscribing to StatusEvents so a command to look up each of the services
  // is needed and something to subscribe maybe.

}
