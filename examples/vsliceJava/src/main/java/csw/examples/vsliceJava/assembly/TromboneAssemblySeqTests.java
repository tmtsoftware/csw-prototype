package csw.examples.vsliceJava.assembly;

/**
 * Created by abrighto on 12/12/16.
 */
public class TromboneAssemblySeqTests {

//
//  /**
//   * TMT Source Code: 12/9/16.
//   */
//
//  object TromboneAssemblySeqTests {
//    LocationService.initInterface()
//
//    val system = ActorSystem("TromboneAssemblySeqTests")
//  }
//  class TromboneAssemblySeqTests  extends TestKit(TromboneAssemblyCompTests.system) with ImplicitSender
//  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {
//
//    implicit val timeout = Timeout(10.seconds)
//
//    val ac = AssemblyTestData.TestAssemblyContext
//  import ac._
//
//    val taName = "lgsTrombone"
//    val thName = "lgsTromboneHCD"
//
//    val componentPrefix: String = "nfiraos.ncc.trombone"
//
//    // Public command configurations
//    // Init submit command
//    val initPrefix = s"$componentPrefix.init"
//    val initCK: ConfigKey = initPrefix
//
//    // Datum submit command
//    val datumPrefix = s"$componentPrefix.datum"
//    val datumCK: ConfigKey = datumPrefix
//
//    val naRangeDistanceKey = DoubleKey("rangeDistance")
//    val naRangeDistanceUnits = kilometers
//
//    // Position submit command
//    val positionPrefix = s"$componentPrefix.position"
//    val positionCK: ConfigKey = positionPrefix
//
//    def positionSC(rangeDistance: Double): SetupConfig = SetupConfig(positionCK).add(naRangeDistanceKey -> rangeDistance withUnits naRangeDistanceUnits)
//
//    def eventPrinter(ev: Event): Unit = {
//      logger.info(s"EventReceived: $ev")
//    }
//
//    val sca1 = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//
//    // This will send a config arg with 10 position commands
//    val testRangeDistance = 90 to 130 by 10
//    val positionConfigs = testRangeDistance.map(f => positionSC(f))
//
//    val sca2 = Configurations.createSetupConfigArg("testObsId", positionSC(100.0))
//
//
//    def getTrombone: BlockingAssemblyClient = resolveAssembly(taName)
//
//    describe("Top Level Sequencer Tests") {
//
//      it("should allow a datum then a set of positions as separate sca") {
//
//        val tlaClient = getTrombone
//        val tla = tlaClient.client.assemblyController
//
//        val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//        var completeMsg = tlaClient.submit(datum)
//
//        // This first one is the accept/verification
//
//        // Second one is completion of the executed ones
//        logger.info("msg2: " + completeMsg)
//        completeMsg.overall shouldBe AllCompleted
//
//        // This will send a config arg with 10 position commands
//        val testRangeDistance = 140 to 180 by 10
//        val positionConfigs = testRangeDistance.map(f => positionSC(f))
//
//        val sca = Configurations.createSetupConfigArg("testobsId", positionConfigs: _*)
//        completeMsg = tlaClient.submit(sca)
//
//
//        logger.info("msg2: " + completeMsg)
//        completeMsg.overall shouldBe AllCompleted
//
//        expectNoMsg(5.seconds)
//
//        tla ! PoisonPill
//      }
//
//      it("should allow a stop from follow mode") {
//        val tlaClient = getTrombone
//        val tla = tlaClient.client.assemblyController
//
//        val fakeSequencer = TestProbe()
//
//
//        val datum = Configurations.createSetupConfigArg("testobsId", SetupConfig(initCK), SetupConfig(datumCK))
//        var completeMsg = tlaClient.submit(datum)
//        expectNoMsg(1.second)
//        logger.info("Datum complete: " + completeMsg)
//
//        val follow = Configurations.createSetupConfigArg("testobsId", setElevationSC(100.0), followSC(false))
//        completeMsg = tlaClient.submit(follow)
//        expectNoMsg(2.seconds)
//        logger.info("Follow complete: " + completeMsg)
//
//        val stop = Configurations.createSetupConfigArg("testObsId", SetupConfig(stopCK))
//        completeMsg = tlaClient.submit(stop)
//        logger.info("Stop complete: " + completeMsg)
//      }
//    }
//  }



}
